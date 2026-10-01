# MCP OAuth 2.1 커넥터 인증 스파이크 (#94)

2026-10-02. 스펙 §7.9의 커넥터 인증이 지금 스택에서 되는지 실제 Claude 커스텀 커넥터로 확인했다. 스파이크 코드는 `spike/collection-mcp-oauth` 브랜치에만 두고 main에 넣지 않는다.

## 결론

된다. 한 Spring 앱 안에서 인가 서버와 `/mcp` 리소스 서버를 같이 띄우고, Claude가 DCR → 로그인 → 토큰 → `save_learning_session` 호출까지 끝냈다. 도구 메서드에서 `SecurityContextHolder`로 로그인 사용자를 바로 꺼낼 수 있다.

## 환경

| 항목 | 값 |
|---|---|
| Spring Boot / Security | 4.1.1 / 7.1.1 |
| Spring AI | 2.0.1 (`spring-ai-starter-mcp-server-webmvc`, `protocol: STREAMABLE`) |
| 추가 의존성 | `org.springaicommunity:mcp-server-security-spring-boot:0.1.14`, `mcp-authorization-server-spring-boot:0.1.14` (0.1.x는 Spring AI 2.0.x 전용) |
| 공개 주소 | ngrok (서버 8091) |
| 클라이언트 | claude.ai 커스텀 커넥터, OAuth Client ID/Secret 칸 비움 |
| 로그인 | 스파이크 전용 폼 로그인 사용자 1명 (소셜 로그인 대신) |

## 확인 결과

| 확인 항목 | 결과 |
|---|---|
| 토큰 없는 `/mcp` | 401 + `WWW-Authenticate: Bearer resource_metadata=<공개주소>/.well-known/oauth-protected-resource/mcp` |
| 보호 리소스 메타데이터 (RFC 9728) | `resource=<공개주소>/mcp`, `authorization_servers=[<공개주소>]` |
| 인가 서버 메타데이터 (RFC 8414) | `registration_endpoint`, `code_challenge_methods_supported=[S256]` 포함 |
| Claude 클라이언트 등록 방식 | **Dynamic Client Registration** (`POST /oauth2/register` 201). CIMD는 쓰지 않았다 |
| 콜백 주소 | `https://claude.ai/api/mcp/auth_callback` |
| 인가 흐름 | `/oauth2/authorize` → `/login` → 로그인 → `/oauth2/authorize` 302 → `/oauth2/token` 200 |
| 동의 화면 | 표시되지 않음 (scope 없는 요청은 라이브러리가 동의를 건너뛴다) |
| 토큰 갱신 | 약 4분 뒤 도구 호출 직전 `/oauth2/token` 200 → 갱신 토큰으로 재발급 확인 |
| 도구 실행 스레드 | 요청 스레드(`http-nio-*-exec-*`)에서 실행, `SecurityContextHolder` 인증 이름 = 로그인 사용자 |
| 저장 | `save_learning_session` 정상 저장 (turns=3, units=1) |

실제 호출 순서 (ngrok 기록):

```text
POST /mcp 401 → GET /.well-known/oauth-protected-resource/mcp → GET /.well-known/oauth-authorization-server
→ POST /oauth2/register 201 → GET /oauth2/authorize 302 → GET /login → POST /login 302
→ GET /oauth2/authorize 302 → POST /oauth2/token 200 → POST /mcp 200 (initialize, tools/list …)
… → POST /oauth2/token 200 (갱신) → POST /mcp 200 (tools/call save_learning_session)
```

## 구성 (스파이크 코드 요약)

`SecurityFilterChain` 3개를 순서대로 둔다. 라이브러리 자동 설정은 직접 체인을 정의하면 꺼진다(`@ConditionalOnDefaultWebSecurity`).

1. `@Order(1)` 인가 서버: `McpAuthorizationServerConfigurer.mcpAuthorizationServer()`, 인가 서버 엔드포인트만 매칭, HTML 요청은 `/login`으로.
2. `@Order(2)` `/mcp`, `/.well-known/oauth-protected-resource/**`: `McpServerOAuth2Configurer.mcpServerOAuth2()`. 같은 앱이므로 issuer URL을 원격 조회하지 않고 `NimbusJwtDecoder.withJwkSource(jwkSource)`를 넘긴다.
3. `@Order(3)` 나머지: 로그인 페이지. (#95에서 `/api/**` 인증으로 바꾼다)

필수 설정:
- `AuthorizationServerSettings.issuer` = 공개 주소(ngrok·배포). 메타데이터·토큰 `iss`가 이 값을 쓴다.
- `server.forward-headers-strategy: framework`. 없으면 프록시 뒤에서 `resource`·`resource_metadata`가 `http://localhost`로 나간다.
- DCR용 `RegisteredClientRepository` 빈. 비어 있으면 안 돼서 쓰지 않는 기본 클라이언트를 하나 넣었다.

## 후속 이슈에 넘길 것

**#95 (앱 로그인)**
- 구글·카카오 redirect URI: Spring Security `oauth2Login` 기본 경로 `{baseUrl}/login/oauth2/code/{registrationId}`
  - 로컬: `http://localhost:8080/login/oauth2/code/google`, `http://localhost:8080/login/oauth2/code/kakao`
  - 배포: `https://<배포 서버>/login/oauth2/code/google`, `.../kakao` (주소 확정 후 등록)
  - ngrok으로 커넥터를 확인할 때는 그 ngrok 주소도 등록해야 한다
- 토큰 `sub`(=인증 이름)를 앱 사용자 ID로 매핑한다. 스파이크는 사용자 이름이 그대로 `sub`였다.

**#96 (MCP OAuth)**
- `/login`(인가 흐름의 로그인 화면)을 구글·카카오 버튼 화면으로 바꾼다. 스펙 §7.9의 "연결 승인"은 지금 동의 화면이 생략되므로, 승인 화면을 직접 넣을지 정해야 한다.
- **재시작에 살아남게** 해야 한다. 스파이크는 모두 메모리였다.
  - 서명 키(JWK)가 시작할 때마다 새로 생겨 재시작하면 발급 토큰이 모두 무효가 된다 → 키를 `.env`로 고정
  - 등록된 클라이언트·인가(갱신 토큰)가 메모리 → 재시작하면 Claude가 다시 연결해야 한다 → 저장소 필요
  - Spring Authorization Server 기본 영속 구현은 `Jdbc*`(JdbcTemplate)다. AGENTS.md "영속성은 JPA만"에 맞추려면 JPA 엔티티로 `RegisteredClientRepository`·`OAuth2AuthorizationService`를 구현한다(Spring Authorization Server 공식 how-to에 JPA 예시 있음).
- 액세스 토큰 기본 수명 5분, 갱신 정상. 그대로 둬도 된다.
- DCR은 공개(인증 없는 등록)다. 아무나 클라이언트를 등록할 수 있지만 토큰은 로그인한 사용자만 받는다. redirect URI를 Claude 콜백과 localhost로 제한할지 결정한다.

## 별개 관찰

- Claude가 `Mcp-Protocol-Version: 2026-07-28`의 `server/discover`를 먼저 보내고, Spring AI 2.0.1이 400(`Session ID missing`)으로 거절하자 `2025-11-25` initialize로 내려가 정상 연결했다. 인증과 무관하며 동작에 지장 없다.
