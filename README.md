# AI Learning Companion

> AI에게 물어본 것을, 내 지식으로 남긴다.

AI 대화를 매일의 맞춤형 학습으로 연결하는 개인 지식 유지 플랫폼입니다.
사용자가 Claude·ChatGPT와 나눈 대화를 등록하면 학습할 지식을 추출하고, 사용자의 학습 목적에 맞는 문제를 제공합니다. 이후 풀이 이력과 FSRS 기억 모델을 바탕으로 매일 복습할 내용을 선정해, 배운 지식을 오래 기억하고 활용하도록 돕습니다.

- 해결할 문제: AI의 설명을 읽고 이해했더라도 시간이 지나면 잊고 같은 내용을 다시 질문하게 됩니다.
- 대상 사용자: AI로 새로운 지식을 배우지만, 별도로 정리하고 복습하기 어려운 대학생·주니어 개발자 등입니다.
- 입력 방식: Claude MCP 커넥터와 대화 공유 링크를 지원하고, 텍스트 붙여넣기를 대체 경로로 제공합니다.
- 학습 경험: 학습 목적 설정 → 첫 문제 풀이 → 힌트·개념 설명 → 여러 세션을 모은 짧은 매일 학습으로 이어집니다.
- 개인화 방식: FSRS로 지식별 기억 상태와 복습 시점을 계산합니다. 기록이 쌓이면 개인별 매개변수를 머신러닝으로 학습하고, 예측 성능이 개선된 경우 문제 선정에 반영합니다.
- 차별화 방향: 대화에서 얻은 지식을 지속적으로 관리하며, 사용자에게 오늘 필요한 문제를 준비합니다. 오개념과 오답뿐 아니라 이전에 맞혔지만 잊을 가능성이 커진 지식도 복습합니다.

## 문서

| 문서 | 내용 |
|---|---|
| [`AGENTS.md`](AGENTS.md) | 사람·AI 에이전트 공통 작업 규칙 (Hard 제약, 소유 경계) |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 브랜치·커밋·PR·이슈 규칙 |
| [`docs/architecture.md`](docs/architecture.md) | 패키지 구조와 아키텍처 규칙 상세 |
| [`docs/jev.md`](docs/jev.md) | Jev 질문 설계·신뢰도 기준·테스트 방법 |
| [`docs/spec/ai-conversation-learning-review-platform.md`](docs/spec/ai-conversation-learning-review-platform.md) | 상세 기획 |
| [`docs/spec/persona-and-domain-stories.md`](docs/spec/persona-and-domain-stories.md) | 페르소나와 도메인 스토리 |
| [`docs/verification/results.md`](docs/verification/results.md) | 검증 기록 |
| [`docs/plans/`](docs/plans/) | 구현 계획 문서 |

## 기술 스택

| 영역 | 기술 | 버전 |
|---|---|---|
| 언어 | Java | 21 (Gradle toolchain) |
| 프레임워크 | Spring Boot (`spring-boot-starter-webmvc`) | 4.1.1 |
| MCP 서버 | Spring AI (`spring-ai-starter-mcp-server-webmvc`, `@McpTool`) | 2.0.1 (BOM) |
| LLM | Spring AI OpenAI (`spring-ai-starter-model-openai`, `ChatClient`) | 2.0.1 (BOM) |
| Jev | TypeSafe System One API (`RestClient` 직접 호출, `jev-latest`) | - |
| 영속성 | Spring Data JPA (Hibernate) | Spring Boot BOM 관리 |
| DB | PostgreSQL (`backend/docker-compose.yml`), 테스트는 H2 PostgreSQL 모드 | 17 |
| 공유 링크 수집 | Playwright for Java | 1.63.0 |
| 빌드 | Gradle (Groovy DSL) + `io.spring.dependency-management` | 1.1.7 |
| 테스트 | JUnit 5 (`spring-boot-starter-webmvc-test`) | Spring Boot BOM 관리 |
| 프론트엔드 | Vite + React + TypeScript (`frontend/`), Vercel 배포 | `frontend/package.json` 기준 |

- 스키마는 해커톤 단계에서 JPA `ddl-auto: update`로 엔티티 기준 자동 갱신한다. 운영 전 마이그레이션 도구로 바꾼다.
- 개발 도구 `/dev/**`는 `.env`의 `DEV_TOOLS_ENABLED=true`일 때만 열린다. 이 기기에서 직접 온 요청은 그대로 허용하고, 원격(배포 서버·터널 경유)은 `X-Dev-Token` 헤더가 `DEV_TOOLS_TOKEN`과 같을 때만 허용한다. 그 외는 404.
  - 배포 서버 예: `curl -X POST -H "X-Dev-Token: $DEV_TOOLS_TOKEN" https://<서버>/dev/clock/travel?days=7`
  - 세션 뷰어: `http://localhost:8080/dev/sessions`
  - 시간 이동 데모: `GET /dev/clock`(현재 시각·오프셋), `POST /dev/clock/travel?days=7&hours=0`(앞으로만), `POST /dev/clock/reset`
- 인증은 없다. 시작할 때 데모 사용자 1명(`review.demo-user.name`)을 만들고 `CurrentUser`가 그 ID를 돌려준다.

의존성 버전은 `backend/build.gradle`이 기준이다. 표와 다르면 build.gradle을 따르고 표를 고친다.

## 실행

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # 시스템 기본 JDK가 21이 아니면 반드시 지정
cp .env.example .env                                 # 공유받은 OPENAI_API_KEY, TYPESAFE_API_KEY 입력 (.env는 커밋 금지)
./gradlew test                                       # Docker 없이 H2로 실행
docker compose up -d --wait                          # PostgreSQL (5432가 사용 중이면 .env의 DB_PORT·DB_URL 변경)
./gradlew bootRun                                    # :8080, 시작 시 데모 사용자 생성

# 컨테이너 (Java 21 + Playwright Chromium 포함)
docker build -t khack-review .
# 컨테이너에서 호스트의 PostgreSQL에 붙으려면 DB_URL의 localhost를 host.docker.internal로 바꾼다
docker run --rm --init --ipc=host -p 8080:8080 --env-file .env -v "$PWD/data:/app/data" khack-review
```

Playwright 버전을 올리면 `backend/Dockerfile`의 `mcr.microsoft.com/playwright/java` 태그도 같은 버전으로 올린다.

### 프론트엔드

```bash
cd frontend
npm install
npm run dev     # :5173, /api·/healthz는 Vite proxy로 localhost:8080에 전달
npm run build   # 타입 검사 + 빌드 (PR 전 필수)
npm run lint
```

- 배포: 프론트엔드는 Vercel(Root Directory `frontend`), 백엔드는 VPS에 따로 올린다.
- 백엔드 API는 `/api/**` 접두사를 쓴다. Vercel에서는 `frontend/vercel.json` rewrites로 `/api/**`를 VPS로 넘겨 같은 출처로 호출한다(VPS 도메인이 정해지면 추가).
- `X-Dev-Token`은 프론트엔드 번들에 넣지 않는다. 배포 환경에서 `/dev/**`는 curl로만 호출한다.
