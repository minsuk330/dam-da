# 커넥터 스키마 v2 Implementation Plan

**Goal:** `save_learning_session`을 spec §7.6 스키마(발화별 의도·AI 판정 추출 + 복습 단위)로 바꾸고, 서버 검증·조회·벤치마크 채점을 붙인다.

**Spec:** `docs/spec/ai-conversation-learning-review-platform.md` §7.6, §8.5 규칙 15·16, §12.2

**Stack:** 기존 `backend/` (Java 21, Spring Boot 4.1.1, Spring AI 2.0.1). `JAVA_HOME`은 temurin-21.

## 결정 사항 (spec 외 구현 판단)

- `totalUserTurns`는 받지 않는다. 모델이 번호를 다시 매겨 누락 감지에 쓸 수 없다고 검증됐다(spec §7.3).
- 기존 JSONL 5건은 변환하지 않는다. `SavedSession`의 `assistantSummary`, `totalUserTurns`는 레거시 읽기용 nullable 필드로 남기고 새 저장에서는 null이다.
- `intent`, `aiVerdict`, `kind`는 Java enum으로 받아 JSON schema에 enum 값이 드러나게 한다. 잘못된 값은 프레임워크 역직렬화 단계에서 거부된다.
- 구조 오류는 `IllegalArgumentException`에 "무엇을 고칠지" 메시지를 모아 던져 도구 오류로 반환한다. 의미상 이상은 `warnings`로 저장한다.

## Tasks

### Task 1: 도메인 타입과 저장소

- `Intent { info_request, rephrase_request, understanding_check, restatement, challenge, meta }`, `AiVerdict { confirmed, partial, corrected, not_applicable }`, `FactKind { fact, warning }` (JSON 값은 소문자 snake_case)
- `UserTurn(index, text, quotedText, intent, answerOpening, answerGist, aiVerdict, correction)`
- `ReviewUnit(title, evidenceTurns, confusionPoints, keyFacts)`, `ConfusionPoint(turn, userBelief, correction)`, `KeyFact(turn, fact, kind)`
- `SessionInput(userTurns, reviewUnits, topicHint)`
- `SavedSession(id, receivedAt, source, transcription, userTurns, reviewUnits, topicHint, warnings, assistantSummary(레거시), totalUserTurns(레거시 Integer))`
- 테스트: v2 저장 왕복, 레거시 줄(answerGist·reviewUnits 없음) 읽기

### Task 2: 서버 검증 (`SessionValidator`)

`validate(SessionInput) → ValidationResult(errors, warnings)`

| 구분 | 규칙 |
|---|---|
| 오류 | userTurns 비어 있음 / 빈 text / index가 1 미만이거나 중복 / reviewUnits 0개 / 단위 title 비어 있음 / evidenceTurns 비어 있음 / 존재하지 않는 index 참조(evidenceTurns, confusionPoints.turn, keyFacts.turn) / confusionPoint가 aiVerdict partial·corrected가 아닌 발화를 가리킴 |
| 경고 | meta 발화가 evidenceTurns·keyFacts 근거로 쓰임 / reviewUnits 8개 이상 / aiVerdict partial·corrected인데 correction 비어 있음 |

오류 메시지는 위치와 고칠 방법을 포함한다. 예: `reviewUnits[1].confusionPoints[0].turn=5: 발화 5의 aiVerdict가 confirmed입니다. partial/corrected 발화만 헷갈린 지점이 될 수 있습니다.`

### Task 3: MCP 도구 v2

- 파라미터: `userTurns`, `reviewUnits`, `topicHint`(선택)
- 도구 설명: spec §7.6의 필드 의미, 의도 6종 정의와 예시, "판정은 새로 하지 말고 대화 속 AI 판정을 추출", `answerOpening`을 따라 AI 답변 단위로 대화를 처음부터 훑을 것, 복습 단위 1~7개·출처 발화 필수·경고는 kind warning, 한 번에 호출
- 검증 오류 → 도구 오류 반환(저장 안 함), 경고 → 저장 후 응답에 경고 개수 포함
- 통합 테스트: 스키마(필수 필드, enum 값), 정상 저장, 구조 오류 거부 메시지, 잘못된 enum 거부

### Task 4: 조회 페이지·Markdown

- 발화: 의도 배지, 인용 대상, 답변 첫 문장, 답변 요약, AI 판정·교정
- 복습 단위: 제목, 근거 발화, 헷갈린 지점(믿음 → 교정), 핵심 사실(경고 강조)
- 경고 목록 표시, 레거시 세션은 기존 요약 표시

### Task 5: 벤치마크 채점

- `backend/fixtures/benchmarks/innodb.json`: 공유 링크 원문 기준 사용자 발화 19개, 헷갈린 지점 10곳(원문 발화 조각 + 기대 판정), 핵심 사실 키워드 그룹, 반대로 적히면 안 되는 문장 패턴
- `BenchmarkScorer.score(benchmark, session)`: ① learning 발화 누락(`TurnComparator`, meta 제외) ② 헷갈린 지점 포착(해당 발화의 aiVerdict가 partial·corrected이거나 confusionPoints가 가리킴) ③ 핵심 사실 키워드 포함 ④ 금지 패턴 검출(warning 없이 등장)
- `./gradlew -q benchmark -Pargs="fixtures/benchmarks/innodb.json [sessionId]"`
- 키워드 채점은 근사치이므로 결과 문서에 수동 검토를 함께 기록한다.

### Task 6: 재측정 (수동)

- 서버 재시작, Claude 커넥터 재연결
- InnoDB 대화: 저장 요청 메시지 수정으로 새 분기 → 채점
- 짧은 대화 1개: 의도별 발화를 섞은 스크립트(`backend/fixtures/scripts/b2-short.json`)로 새 대화 → 채점
- `docs/verification/results.md`에 v1 대비 결과 기록
