# 협업 규칙

2인 해커톤 팀 기준. 속도 우선, 승인 없이 셀프 merge.

## 소유 영역

| 영역 | 담당 | 범위 |
|---|---|---|
| server | 서버 담당 | `collection/`, `practice/`, `memory/`, `engagement/`, `common/`, 웹·MCP 어댑터, `port/out` 인터페이스·record·fake, `frontend/` |
| ai | AI 담당 | 포트 구현체(`common/adapter/out/openai`, `common/adapter/out/typesafe` 등), 프롬프트, Jev 질문 정의, 벤치마크, 문제 생성·품질 검사·변형 구현 |

상대 영역은 직접 고치지 않는다. 이슈나 PR 코멘트로 제안한다. 상세 경계는 [`docs/architecture.md`](docs/architecture.md#포트-경계와-소유).

## 브랜치

- GitHub Flow. `main`은 항상 동작하는 상태로 유지하고 직접 push하지 않는다.
- 브랜치 이름: `<type>/<context>-<요약>` (예: `feat/memory-fsrs-scheduler`, `fix/question-quality-check`).
- merge는 squash merge만 쓴다.

## 커밋·PR

- [Conventional Commits](https://www.conventionalcommits.org/): `feat`, `fix`, `refactor`, `test`, `docs`, `chore`. prefix는 영어, 본문은 한국어 가능.
- squash merge라 **PR 제목이 최종 커밋 메시지**다. PR 제목도 같은 규칙을 따른다.
- 커밋에 AI attribution 줄(`Co-Authored-By` 등)을 넣지 않는다.
- PR 본문에 관련 이슈를 `Closes #N`으로 연결한다.

## merge 조건

- 승인 불필요. 작성자가 직접 merge한다.
- CI는 없다. **PR 올리기 전 로컬에서 `./gradlew test` 통과 필수.**
- 실제 LLM을 호출하는 테스트(`./gradlew liveTest`, `@Tag("live")`)와 벤치마크(`./gradlew benchmark`)는 로컬에서 수동 실행한다. LLM·Jev를 부르는 흐름을 바꾼 PR은 `liveTest` 결과를 본문에 적는다. 프롬프트·커넥터 스키마를 바꾼 PR은 벤치마크 결과 요약을 본문에 적는다.
- 포트 계약 변경은 인터페이스·record·fake만 담은 PR을 먼저 merge한 뒤 구현한다.

## 이슈

- 할 일은 GitHub Issues로 관리한다.
- 라벨: `owner:server`, `owner:ai`.

## 비밀 정보

- OpenAI·TypeSafe(Jev) 키는 팀 공용 1개씩. 각자 로컬 `backend/.env`에 넣는다. 다른 파일에 키를 넣지 않는다.
- `.env`는 커밋하지 않는다. 새 변수를 추가하면 `backend/.env.example`에 빈 값으로 추가한다.
