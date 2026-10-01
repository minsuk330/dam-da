# AGENTS.md

사람과 AI 에이전트(Codex, Claude) 공통 규칙. 이 파일이 단일 원본이고 `CLAUDE.md`는 이 파일을 import한다.

## 프로젝트

AI 대화를 등록하면 학습할 지식을 추출해 문제로 만들고, FSRS로 매일 복습할 내용을 고르는 개인 지식 유지 플랫폼.

- 제품 개요·스택: [`README.md`](README.md)
- 상세 기획: [`docs/spec/ai-conversation-learning-review-platform.md`](docs/spec/ai-conversation-learning-review-platform.md)
- 페르소나와 도메인 스토리: [`docs/spec/persona-and-domain-stories.md`](docs/spec/persona-and-domain-stories.md)
- 아키텍처 상세: [`docs/architecture.md`](docs/architecture.md)
- 협업 규칙 상세: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- 검증 기록: [`docs/verification/results.md`](docs/verification/results.md), 계획 문서: [`docs/plans/`](docs/plans/)

## 실행·테스트

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # 시스템 기본 JDK가 21이 아니면 반드시 지정
./gradlew test        # PR 전 필수. 실제 LLM 호출 없이 fake로 통과해야 한다
./gradlew liveTest    # 실제 Jev·OpenAI로 핵심 흐름 검증(@Tag("live")). backend/.env 키 필요, 비용 발생. worktree면 -PenvFile=경로
./gradlew benchmark   # 커넥터 저장 결과 채점. 서버(bootRun, DEV_TOOLS_ENABLED=true)가 떠 있어야 함. 다른 포트면 -Pserver=
./gradlew bootRun     # :8080
```

## Hard 제약

위반하면 제품 전제가 깨지는 규칙이다. 바꿔야 하면 코드보다 먼저 사용자와 합의하고 스펙을 고친다.

### 역할 분리
- **LLM = 생성, Jev = 판정, 코드 = 계산.** 문제·힌트·설명·변형 문제는 LLM, 품질 검사·답변 판정·다음 행동 선택은 Jev(정해진 선택지 안의 구조화 판정), 기억 상태·복습 시점은 일반 코드가 계산한다.
- 복습 시점·망각 예측을 LLM/Jev에게 묻지 않는다. FSRS로 계산한다.
- 개인별 FSRS 매개변수는 기본 매개변수보다 예측 성능이 개선됐음을 검증한 경우에만 적용한다.

### 입력·커넥터
- MCP 서버는 저장 도구 `save_learning_session` 하나만 노출한다. 복습 조회·답변 제출 등 채팅 안 복습 도구를 추가하지 않는다. 복습 풀이는 앱에서만 한다.
- 커넥터 스키마는 v5로 확정됐다(태그 `connector-schema-v5`, 스펙 §7.6). 스키마나 도구 설명을 바꾸면 고정 벤치마크(`./gradlew benchmark`)로 비교한 뒤 버전을 올린다. 도구 설명 변경은 Claude 커넥터 재연결이 필요하다.
- 서버 검증: 서버가 고칠 수 없는 구조 오류만 거부하고, 의미상 이상은 저장 후 경고로 남긴다.
- 모든 학습 대화는 입력 경로와 원문 여부(`verbatim` / `model_transcribed`)를 가진다. 커넥터 수신 발화는 사용자 확인 전 원문으로 취급하지 않는다.

### 학습 도메인
- 모든 복습 단위·문제는 원문 근거 발화를 가진다. `meta` 발화는 근거가 될 수 없다.
- 문제 후보는 품질 검사(Jev)를 통과하기 전 사용자에게 노출하지 않는다.
- 문제 생성은 사용자가 복습 단위를 확인한 뒤에 한다.
- 판정 신뢰도가 기준보다 낮으면 기억 상태를 자동으로 바꾸지 않는다.
- 대화 내 AI 판정(`aiVerdict`)은 추출된 값이며, 사용자 답변 판정이나 사실 검증을 대체하지 않는다.

### 코드
- 영속성은 JPA만 쓴다. MyBatis·jOOQ·JdbcTemplate 직접 SQL을 추가하지 않는다. 필요한 경우 JPQL 또는 Spring Data 쿼리 메서드, 불가피하면 `@Query(nativeQuery = true)`.
- 현재 시각은 주입받은 `Clock`으로만 얻는다(`LocalDateTime.now()` 등 직접 호출 금지). 시간 이동 데모 모드의 전제다.
- API 키·ngrok URL·공유 링크 등 비밀/개인 데이터는 커밋하지 않는다. 키는 `backend/.env`(gitignore)에만 둔다.
- `/dev/**` 개발 도구(세션 뷰어, 시간 이동)는 `DEV_TOOLS_ENABLED=true`일 때만 열린다. 로컬 직접 요청 또는 `X-Dev-Token` 헤더가 `DEV_TOOLS_TOKEN`과 같은 원격 요청만 허용한다. 토큰은 커밋하지 않고, 프론트엔드 번들에도 넣지 않는다.

## 아키텍처 (필수)

완화된 헥사고날. 상세·패키지 트리는 [`docs/architecture.md`](docs/architecture.md).

- 최상위 패키지 = 스펙 §9 컨텍스트: `collection/`, `analysis/`, `question/`, `practice/`, `memory/`, `engagement/`, `common/`. 예외: `tools/`(개발 도구 CLI).
- 컨텍스트 내부: `domain/`(JPA 엔티티 + JpaRepository) → `application/`(서비스, `port/out/`) ← `adapter/`(`in/web`, `in/mcp`, `out/<system>`).
- 의존 방향 `adapter → application → domain`. `domain/`은 Spring Web·Spring AI·Playwright·HTTP 클라이언트에 의존하지 않는다.
- 외부 시스템(LLM, Jev, 공유 링크 수집, 알림)만 `port/out` 뒤에 둔다. 영속성·인바운드 포트는 만들지 않는다.
- 컨텍스트 간에는 ID로 참조하고, 다른 컨텍스트 기능은 그 컨텍스트의 application 서비스로 호출한다.
- API 입출력은 DTO(record). 엔티티를 밖으로 노출하지 않는다.
- `tools/`는 개발 도구 CLI 예외 패키지다. 런타임 코드가 의존하지 않는다.

## 소유 경계

| 영역 | 범위 |
|---|---|
| server | `collection/`, `practice/`, `memory/`, `engagement/`, `common/`, 웹·MCP 어댑터, `port/out` 인터페이스·record·fake, `frontend/` |
| ai | 포트 구현체(`common/adapter/out/openai`, `common/adapter/out/typesafe` 등), 프롬프트, Jev 질문 정의, 벤치마크, 문제 생성·품질 검사·변형 구현 |

### AI 영역 규칙
- LLM·Jev 호출은 포트 구현체 안에서만 한다. application 서비스에서 `ChatClient`를 직접 쓰지 않는다.
- 모든 포트는 fake 구현을 가진다. `./gradlew test`는 OpenAI·TypeSafe 키 없이 통과해야 한다.
- Jev 질문 설계·신뢰도 기준·소유 경계는 [`docs/jev.md`](docs/jev.md)를 따른다. 실제 API 확인은 `./gradlew -q jevCheck`.
- 프롬프트·커넥터 스키마를 바꾸면 `./gradlew benchmark`를 돌리고 결과 요약을 PR 본문에 적는다.

## 에이전트 행동 규칙

- 작업은 feature 브랜치(`<type>/<context>-<요약>`)에서 한다. 커밋·push는 허용한다.
- 금지: `main`에 직접 push, force push, PR merge. merge는 사람이 한다.
- 지시받은 작업의 소유 영역 밖 파일은 수정하지 않는다. 필요하면 변경 제안만 남긴다.
- 커밋 전·PR 전 `./gradlew test`를 통과시킨다. 실패하면 커밋하지 말고 보고한다.
- 커밋·PR 제목은 Conventional Commits(prefix 영어). AI attribution 줄(`Co-Authored-By` 등)을 넣지 않는다.
- Hard 제약에 닿는 변경은 진행 전에 사용자에게 확인한다.

## 협업 요약

GitHub Flow + squash merge, 승인 없이 셀프 merge, CI 없음(로컬 테스트 필수), 이슈는 GitHub Issues(`owner:server` / `owner:ai`). 상세는 [`CONTRIBUTING.md`](CONTRIBUTING.md).
