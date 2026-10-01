# Jev 사용 가이드

Jev는 TypeSafe의 System One 모델이다. **정해진 선택지 안의 구조화 판정**만 맡는다(스펙 §6.2). 생성은 LLM, 복습 시점은 FSRS.

- API 원본 문서: https://docs.typesafe.ai/api
- 포트: `common/application/port/out/JevPort`
- 어댑터: `common/adapter/out/typesafe/TypeSafeJevAdapter` (POST `/v1/systemone`)
- 테스트 대역: `src/test/.../common/application/port/out/FakeJevPort`

## 질문 유형

유형 3개는 API가 고정한다. 질문 문장·선택지·단계·질문 개수는 우리가 정한다.

| 유형 | 쓰는 곳 | 답 | 제약 |
|---|---|---|---|
| `JevQuestion.noul` | 예/아니오 판단 | "예" 확률 0~1 (신뢰도 없음) | — |
| `JevQuestion.choice` | 정해진 상태 중 하나 고르기 | 선택지 + 선택지별 확률 + 신뢰도 | 선택지 최대 255개 |
| `JevQuestion.score` | 순서 있는 단계로 평가 | 단계 사이 가중 점수(0부터) + 신뢰도 | 단계 2~10개 |

- `state`는 문자열 또는 record·Map. 질문 문장에서 state 필드를 백틱으로 가리킨다(예: "`answer`는 `criteria`를 충족하는가?").
- 관련 질문은 **한 번의 호출에 묶는다.** 질문마다 호출하지 않는다(비용·지연).
- 선택지·단계 설명은 지금 `String`만 받는다. 구조화 설명이 필요하면 `JevQuestion`의 해당 타입을 `Object`로 넓힌다.

## 어디에 무엇을 두나

판정은 각 컨텍스트의 application 서비스가 `JevPort`를 주입받아 한다. 공용 판정 서비스를 `common/`에 만들지 않는다.

| 판정 | 컨텍스트 | 예시 |
|---|---|---|
| 복습 단위 검수 (복습 가치, 근거 연결) | `analysis/` | noul `worth_reviewing`, score `evidence_fit` |
| 문제 품질 검사 (근거성, 명확성, 난이도, 중복) | `question/` | noul `grounded`, score `clarity`·`difficulty`, noul `duplicate` |
| 답변 판정 | `practice/` | choice `verdict`·`misread`, noul `omission`·`contradiction`·`repeats_user_belief`·`off_target_error` |
| 다음 행동 선택 | `practice/` | choice `next_action` |

**소유 경계:**

- 질문 정의(질문 문장, 선택지·단계 설명)는 사실상 프롬프트다 → **ai 소유.** 판정 서비스 옆 별도 클래스에 둔다(예: `practice/application/AnswerJudgeQuestions`).
- 판정 서비스(호출, 결과 해석, 기억 상태 반영, 신뢰도 기준 적용)는 **server 소유.**

## 신뢰도 기준 (Hard 제약)

> 판정 신뢰도가 기준보다 낮으면 기억 상태를 자동으로 바꾸지 않는다.

- 기준 적용은 **판정 서비스 책임**이다. 포트·어댑터는 기준을 모른다.
- 기준값은 서비스 코드에 숫자로 박지 않고 `application.yml`(`review.<context>.min-confidence` 등)로 뺀다. 실제 데이터로 맞춘다.
- noul은 신뢰도가 없다. 확률이 0.5 근처(예: 0.3~0.7)면 판정 보류로 취급한다.
- 커넥터 입력(`model_transcribed`)에 근거한 판정은 기준을 더 높게 잡는다(스펙 §7.3).
- 참고: 예시 호출에서 choice 신뢰도는 0.44~0.88로 나왔다. 질문 설계에 따라 크게 달라진다.

## 답변 판정 (스펙 §6.4.5)

질문 정의는 `practice/application/AnswerJudgeQuestions`(ai 소유), 호출·해석은 `AnswerJudge`, 기록은 `AnswerJudgment`(server 소유)다. 질문은 한 번의 호출에 묶는다.

| 질문 | 유형 | 기록 |
|---|---|---|
| `verdict` | choice `met` / `not_met` / `unable_to_judge` | 판정과 choice 신뢰도 |
| `omission` | noul | 필수 기준 중 답변에 없는 것이 있음 |
| `contradiction` | noul | 평가 대상 기준을 부정하거나 틀리게 주장. 올바른 내용을 말하고 같은 답변에서 뒤집어도 해당 |
| `misread` | choice `misread` / `answered_as_asked` / `question_unclear` | 질문을 다르게 이해함. 선택과 그 신뢰도를 `verdict`와 따로 남긴다 |
| `repeats_user_belief` | noul, 헷갈린 지점 항목만 | 틀린 주장이 대화 속 `userBelief`와 같은가. `contradiction`이 확정된 경우에만 오개념 재발 |
| `off_target_error` | noul | 평가 대상 밖의 틀린 내용. 등급에 반영하지 않는다 |

```java
// practice/application/AnswerJudge (server 소유)
JevResult r = jev.evaluate(state, AnswerJudgeQuestions.questions(state.userBelief() != null));
JevAnswer.Choice verdict = r.choice(AnswerJudgeQuestions.VERDICT);   // met / not_met / unable_to_judge
JevAnswer.Choice misread = r.choice(AnswerJudgeQuestions.MISREAD);   // misread / answered_as_asked / question_unclear
```

- 상태(`AnswerJudgeState`): `question`, `type`, `answerCriteria`, `modelAnswer`, `answer`, `item`, 헷갈린 지점 항목이면 `userBelief`와 `correction`. 대화 당시의 `aiVerdict`와 근거의 원문 여부는 Jev에 보내지 않는다(전자는 현재 답변의 정오가 아니고, 후자는 코드가 기준을 고르는 데 쓴다).
- 정오는 `answerCriteria`만으로 정한다. `modelAnswer`는 참고용이라 거기에만 있는 내용을 필수로 삼지 않는다.
- **`misread`는 choice로 묻는다.** noul은 확률만 있고 신뢰도가 없는데, 등급 변환표 행 2는 `misread`의 신뢰도가 필요하다. noul로 물었을 때는 평범한 오답에도 확률이 0.5~0.9로 나와 오답이 보류로 빠졌다. `question_unclear`는 문제가 모호한 경우이며 학습자의 오해로 보지 않는다.
- **이유(noul)는 애매하면 확정하지 않는다.** 확률이 `review.practice.judge.reason-yes-min`(0.7) 이상이면 있고 `reason-no-max`(0.3) 이하이면 없다. 그 사이는 참·거짓으로 정하지 않고 `AnswerJudgment.ambiguousReasons`에 적는다. 원래 확률은 `...Probability`에 그대로 남는다.
- 이유는 `verdict`가 `not_met`일 때만 읽는다. 대표 이유는 contradiction > omission > misread. `met`에서도 `omission` 확률이 0.3~0.7로 나오는 일이 흔하므로 `met`의 이유 확률을 해석하지 않는다.
- 판정 서비스는 신뢰도 기준을 적용하지 않는다. 기준은 등급 변환(`RatingPolicy`)이 적용한다.
  - `review.memory.rating.min-confidence`(0.70, 데모 기간 값): `verdict` 신뢰도가 이보다 낮거나 `unable_to_judge`이면 보류, `misread`가 이 이상의 신뢰도로 확인되면 보류. `not_met`에서 `misread` 신뢰도만 부족하면 Again이다.
  - `review.memory.rating.min-confidence-transcribed`(0.80): 근거가 `model_transcribed`일 때의 기준(스펙 §7.3). **검증하지 않은 초기값**이다.
  - 기준값은 Jev의 채점 정확도를 뜻하지 않고 자동 반영 여부를 정하는 초기 기준이다.
  - 풀이 기록(`ReviewLog`)에 판정값·신뢰도·정책 버전·변환표 행과 함께 적용한 기준(`appliedMinConfidence`)과 근거의 원문 여부(`evidenceTranscribed`)를 남긴다.
- 실제 Jev 분포는 `./gradlew -q answerJudgeCheck`로 본다(표본 13개). 2026-10-02 측정에서 표현이 다른 정답의 `verdict` 신뢰도가 0.64~0.83으로 나와, 0.80 기준에서는 맞는 답의 일부가 보류된다. 그래서 데모 기간에는 0.70/0.80을 쓰고, 풀이 기록으로 보정한다.
- `off_target_error`는 문구에 따라 평가 대상 안의 오류를 세거나(오탐) 진짜 대상 밖 오류를 놓쳤다. 기록 전용이며 정오와 등급에 쓰지 않는다.
- 객관식은 Jev 없이 코드가 채점한다(`MET`/`NOT_MET`, 신뢰도 1).
- 답을 제출하면 그 응답 안에서 판정한다. Jev 호출은 트랜잭션 밖에서 한다.

## 다음 행동 선택 (스펙 §6.2, §7 5단계)

질문 정의는 `practice/application/NextActionQuestions`(ai 소유), 허용할 행동은 상태 규칙 `FeedbackRules`, 호출과 신뢰도 기준 적용은 `NextActionJudge`(server 소유)다.

- 선택지는 상태 규칙이 허용한 행동만 담는다. 허용된 행동이 하나면 Jev를 부르지 않는다. 신뢰도가 `review.practice.feedback.min-confidence`(0.5)보다 낮거나 호출이 실패하면 규칙의 기본 행동을 쓴다.
- 다음 행동은 학습 흐름만 바꾸고 FSRS 등급에는 관여하지 않는다.
- 상태(`NextActionState`)에는 시도의 경과만 있고 사용자 답과 정답 기준은 없다. 판단은 아래 원칙으로 한다(질문 문장에 담겨 있다).
  1. `repeatedDifficulty`가 참이면: 틀렸으면 힌트 대신 개념 설명, 도움을 보고 맞혔으면 오늘 한 번 더 묻기.
  2. 처음 틀렸으면: 첫 학습은 설명보다 힌트를 먼저, 매일 학습은 설명 없이 오늘 끝에 다시 묻기(다시 묻기를 못 하면 설명).
  3. 도움을 보고 맞혔으면: 오늘 한 번 더 묻기. 첫 학습의 도움 후 정답은 상태 규칙이 확인 문제를 바로 편성해 Jev를 부르지 않는다(#90, 스펙 §11.3 7단계). 매일 학습에는 힌트 단계가 없으므로 Jev가 보는 경우는 매일 학습의 설명 후 정답이다.
- 2026-10-02 측정(`./gradlew -q nextActionCheck`, 표본 8개): 자리표시 문구는 기대와 3/8 일치(신뢰도 0.39~0.55가 많아 기본 행동으로 물러남, 매일 학습의 첫 오답을 0.86으로 설명으로 고름) → 원칙을 넣은 뒤 8/8 일치, 신뢰도 0.63~0.99.
- 2026-10-02 재측정(#90 반영, 표본 7개): 첫 학습 도움 후 정답 표본 2개를 빼고(Jev에 오지 않음) 매일 학습 설명 후 정답(여러 번 틀려 온 항목)을 넣었다. 3회 모두 7/7 일치, 최저 신뢰도 0.56~0.64. `ADVANCE` 설명을 "스스로 맞혔을 때"만 남기자 다시 묻기 상한 표본이 0.34~0.41로 기준 아래로 떨어져, "설명을 아직 보지 않았으면 고르지 않는다"를 더했다.

## 힌트·개념 설명 생성 (스펙 §7 5단계)

Jev가 아니라 LLM이 만든다. 포트는 `practice/application/port/out/FeedbackContentGenerator`, 구현은 `practice/adapter/out/llm/LlmFeedbackContentGenerator`와 `FeedbackPrompt`(ai 소유)다.

- 힌트는 정답을 말하지 않고 떠올릴 방향만 알려 준다. 모범 답안·정답 기준·교정의 문구가 힌트에 통째로 들어 있으면 코드가 거부한다. 표현을 바꾼 노출은 코드로 잡지 못한다.
- 개념 설명은 헷갈린 지점이면 당시 믿음(`userBelief`)과 교정을 비교한다. 근거 발화는 요청에 있던 index만 돌려준다.
- 학습자가 기다리는 자리라 다시 요청하지 않는다. 비었거나 너무 길거나 호출이 실패하면 `FeedbackGenerationException`이고, 서버가 문제에 저장된 기본 힌트·설명으로 대신한다.
- 결과를 눈으로 볼 때는 `./gradlew -q feedbackContentCheck -Pargs="--out build/feedback-content.json"`.

## 오류와 재시도

- 실패는 `JevCallException`(`status()` = HTTP 상태, 연결 오류는 0).
- 어댑터는 **재시도하지 않는다.** `retryable()`(429 한도 초과, 529 과부하)이면 호출하는 쪽에서 짧게 기다렸다가 재시도한다. 사용자 답변 판정이 실패하면 기억 상태를 바꾸지 않고 판정 보류로 둔다.
- 키가 없으면 앱은 뜨고, 호출 시 `IllegalStateException`.

## 테스트

| 대상 | 방법 | 키 |
|---|---|---|
| 판정 서비스 | `FakeJevPort().willReturn(...)` / `willFail(...)` | 불필요 |
| 어댑터 | `TypeSafeJevAdapterTest` (MockRestServiceServer) | 불필요 |
| 실제 API | `./gradlew -q jevCheck -Pargs="<사용자 답변>"` | `backend/.env`의 `TYPESAFE_API_KEY` |
| 복습 단위 검수 실제 API | `./gradlew -q unitReviewCheck` (예시 단위 3개: 통과·복습 가치 없음·근거 연결 부족) | `backend/.env`의 `TYPESAFE_API_KEY` |
| 답변 판정 질문 실제 API | `./gradlew -q answerJudgeCheck` (표본 13개: 표현이 다른 정답, 누락, 번복, 모호한 답, 질문 오독, 대상 밖 오류, 믿음 반복) | `backend/.env`의 `TYPESAFE_API_KEY` |
| 다음 행동 질문 실제 API | `./gradlew -q nextActionCheck` (표본 8개: 첫 학습·매일 학습, 처음 틀림·반복 어려움, 힌트·설명 뒤 정답) | `backend/.env`의 `TYPESAFE_API_KEY` |

`./gradlew test`는 `application-test.yml`에서 키를 비워 `.env`에 키가 있어도 실제 Jev를 부르지 않는다.

`jevCheck`는 예시 질문(답변 상태·관련성·명확성)으로 실제 API를 한 번 호출하고 결과를 출력한다. 질문 설계를 바꿀 때 실제 확률·신뢰도 분포를 보는 용도다. 로컬 수동 실행만 한다.
