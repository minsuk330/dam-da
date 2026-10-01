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
| 답변 판정 | `practice/` | choice `verdict`, noul `omission`·`contradiction`·`misread`·`repeats_user_belief`·`off_target_error` |
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
| `omission` | noul | 필수 내용 누락 |
| `contradiction` | noul | 평가 대상 기준을 부정하거나 틀리게 주장 |
| `misread` | noul | 질문을 다르게 이해함. 확률을 등급 변환의 `misread` 신뢰도로 쓴다 |
| `repeats_user_belief` | noul, 헷갈린 지점 항목만 | 틀린 주장이 대화 속 `userBelief`와 같은가. `contradiction`과 함께면 오개념 재발 |
| `off_target_error` | noul | 평가 대상 밖의 틀린 내용. 등급에 반영하지 않는다 |

```java
// practice/application/AnswerJudge (server 소유)
JevResult r = jev.evaluate(state, AnswerJudgeQuestions.questions(state.userBelief() != null));
JevAnswer.Choice verdict = r.choice(AnswerJudgeQuestions.VERDICT);   // met / not_met / unable_to_judge
double misread = r.noul(AnswerJudgeQuestions.MISREAD).probability();
```

- 상태(`AnswerJudgeState`): `question`, `type`, `answerCriteria`, `modelAnswer`, `answer`, `item`, 헷갈린 지점 항목이면 `userBelief`와 `correction`.
- 이유(noul)는 확률이 `review.practice.judge.failure-threshold` 이상이고 `verdict`가 `not_met`일 때만 있다고 본다. 대표 이유는 contradiction > omission > misread.
- 판정 서비스는 확률을 그대로 남기고 신뢰도 기준을 적용하지 않는다. 기준은 등급 변환(`RatingPolicy`, `review.memory.rating.min-confidence`)이 적용하며, 근거가 `model_transcribed`인지(`evidenceFidelity`)를 함께 남겨 더 높은 기준을 고를 수 있게 한다.
- 객관식은 Jev 없이 코드가 채점한다(`MET`/`NOT_MET`, 신뢰도 1).
- 답을 제출하면 그 응답 안에서 판정한다. Jev 호출은 트랜잭션 밖에서 한다.

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

`./gradlew test`는 `application-test.yml`에서 키를 비워 `.env`에 키가 있어도 실제 Jev를 부르지 않는다.

`jevCheck`는 예시 질문(답변 상태·관련성·명확성)으로 실제 API를 한 번 호출하고 결과를 출력한다. 질문 설계를 바꿀 때 실제 확률·신뢰도 분포를 보는 용도다. 로컬 수동 실행만 한다.
