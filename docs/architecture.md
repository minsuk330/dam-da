# 아키텍처

**완화된 헥사고날 아키텍처.** 필수 규칙이다. JPA만 쓰므로 영속성은 포트/어댑터로 감싸지 않고, 외부 시스템만 포트로 분리한다.

## 패키지 구조

스펙 §9 도메인 경계 단위로 최상위 패키지를 나눈다.

```text
com.khack.review
├── collection/     대화 수집 (커넥터, 공유 링크, 붙여넣기)
├── analysis/       학습 내용 분석 (스키마 검증, 복습 단위)
├── question/       문제 관리 (생성, 품질 검사, 변형)
├── practice/       복습 진행 (세션, 풀이, 판정)
├── memory/         기억 관리 (FSRS, 복습 큐)
├── engagement/     참여 유도 (알림)
├── common/         Clock, 공용 LlmPort(OpenAI 어댑터) 등 공통 설정
└── tools/          개발 도구 CLI (벤치마크·검증). 컨텍스트 규칙의 예외
```

각 컨텍스트 내부:

```text
<context>/
├── domain/                 엔티티(@Entity), 값 객체, 도메인 규칙, JpaRepository 인터페이스
├── application/            유스케이스 서비스(@Transactional)
│   └── port/out/           외부 시스템 인터페이스 (LLM, Jev, 공유 링크 수집, 알림)
└── adapter/
    ├── in/web/             REST 컨트롤러
    ├── in/mcp/             @McpTool
    └── out/<system>/       port/out 구현 (LLM 클라이언트, Playwright 등)
```

## 규칙

1. 의존 방향: `adapter → application → domain`. 역방향 금지.
2. **완화 1 — 도메인 = JPA 엔티티.** 도메인 모델에 `jakarta.persistence` 애노테이션을 허용한다. 별도 영속성 모델·매퍼를 만들지 않는다.
3. **완화 2 — 리포지토리 포트 생략.** `JpaRepository` 인터페이스를 `domain/`에 두고 application 서비스가 직접 쓴다. 영속성 어댑터를 만들지 않는다.
4. **완화 3 — 인바운드 포트 생략.** 컨트롤러·MCP 도구는 application 서비스를 직접 호출한다.
5. 외부 시스템(LLM, Jev, 공유 링크 수집, 알림)은 반드시 `port/out` 인터페이스 뒤에 둔다. 테스트는 fake 구현으로 대체한다.
6. `domain/`은 Spring Web, Spring AI, Playwright, HTTP 클라이언트에 의존하지 않는다. 비즈니스 규칙은 엔티티·도메인 서비스에 두고 application 서비스는 흐름 조율만 한다.
7. 컨텍스트 간에는 엔티티를 직접 참조하지 않고 ID로 참조한다. 다른 컨텍스트의 기능은 그 컨텍스트의 application 서비스를 통해 호출한다.
8. 컨트롤러·MCP 요청/응답은 DTO(record)로 받고 내보낸다. 엔티티를 API 밖으로 노출하지 않는다.

## 포트 경계와 소유

server/ai 두 영역은 `port/out`에서 만난다.

- **server 소유:** `port/out` 인터페이스, 입출력 record, fake 구현, 포트를 호출하는 application 서비스.
- **ai 소유:** 포트 구현체(`common/adapter/out/openai`, `common/adapter/out/typesafe` 등), 프롬프트, Jev 질문 정의, 벤치마크. Jev 상세는 [`jev.md`](jev.md).

포트 계약(인터페이스·record)을 바꿀 때는 인터페이스·record·fake만 담은 작은 PR을 먼저 올리고 merge한 뒤 각자 구현한다. 승인은 받지 않는다.

## tools 패키지 (예외)

`tools/`는 스펙 §9 컨텍스트가 아니라 `./gradlew benchmark`, `connectorCheck`, `shareVerify` 등 Gradle `JavaExec` CLI를 모은 개발 도구다.

- `tools/benchmark/`: 커넥터 추출 고정 벤치마크 (ai 소유).
- 저장된 대화는 DB에 있으므로 CLI는 실행 중인 서버의 `/dev/sessions.json`에서 읽는다(`tools/verify/SessionSource`). 서버 주소는 `-Pserver=`(기본 `http://localhost:8080`), 원격이면 `DEV_TOOLS_TOKEN` 환경 변수를 헤더로 보낸다.
- `tools/verify/`: 입력 경로 검증 CLI와 비교기.
- Spring 빈을 두지 않는다. 런타임 코드(컨텍스트 패키지)는 `tools/`에 의존하지 않는다. `tools/`는 컨텍스트를 자유롭게 쓴다.

## 임시 상태

- 학습 대화의 복습 단위·경고는 `learning_conversation` 테이블에 커넥터가 보낸 JSON 모양 그대로 보관한다. 분석 컨텍스트(#6)가 엔티티로 옮긴다.
- 커넥터 스키마 검증(`SessionValidator`)은 `collection/`에 둔다. 구조 오류는 저장 전에 입구에서 거부해야 하고, analysis로 옮기면 두 컨텍스트가 서로를 참조하게 된다. analysis는 검증을 통과해 저장된 대화의 `ConversationSubmitted` 이벤트를 받아 학습 세션을 만든다.
