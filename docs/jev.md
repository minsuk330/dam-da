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
| 답변 판정 | `practice/` | choice `status` |
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

## 스펙 §6.2 판정 → 질문 설계 예시

```java
// practice/application/AnswerJudgeQuestions (ai 소유)
static Map<String, JevQuestion> questions() {
    Map<String, String> status = new LinkedHashMap<>();
    status.put("correct", "정답 기준을 모두 충족");
    status.put("partial", "정답 기준을 일부만 충족");
    status.put("misconception", "틀린 개념을 사실로 믿고 있음");
    status.put("misunderstood_question", "질문 자체를 다르게 이해함");
    status.put("lucky_guess", "결론은 맞지만 근거가 틀리거나 없음");
    status.put("unable_to_judge", null);
    return Map.of("status", JevQuestion.choice("`criteria` 기준으로 `answer`는 어떤 상태인가?", status));
}

// practice/application/AnswerJudgeService (server 소유)
JevResult r = jev.evaluate(Map.of("question", q, "criteria", c, "answer", a), AnswerJudgeQuestions.questions());
JevAnswer.Choice status = r.choice("status");
if (status.confidence() < minConfidence) {
    // 기억 상태 변경 안 함, 사용자 확인 요청
}
```

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

`jevCheck`는 예시 질문(답변 상태·관련성·명확성)으로 실제 API를 한 번 호출하고 결과를 출력한다. 질문 설계를 바꿀 때 실제 확률·신뢰도 분포를 보는 용도다. 로컬 수동 실행만 한다.
