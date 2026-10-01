package com.khack.review.practice.application;

import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.practice.domain.FeedbackAction;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 다음 행동 선택 Jev 질문 정의 (스펙 §6.2, §7 5단계, docs/jev.md). 질문 문장·선택지 설명은 프롬프트이므로 ai 소유다.
 * 바꿀 때는 {@code ./gradlew -q nextActionCheck}로 실제 선택과 신뢰도를 본다.
 * 선택지는 상태 기계({@code FeedbackRules})가 허용한 행동만 담는다. 판정 서비스({@link NextActionJudge})는 질문 이름과 선택지 이름에만 의존한다.
 *
 * <p>판단 원칙(질문 문장에 담는다)
 * <ul>
 *   <li>스스로 떠올릴 기회를 먼저 준다. 처음 틀렸으면 설명보다 힌트가 먼저다.</li>
 *   <li>여러 번 틀려 온 항목({@code repeatedDifficulty})은 힌트로 부족하므로 설명하고, 도움을 보고 맞혔어도 오늘 한 번 더 묻는다.</li>
 *   <li>도움을 보고 맞힌 답은 스스로 떠올린 것이 아니다. 설명을 보고 맞혔으면 오늘 한 번 더 묻고, 힌트만 보고 맞혔으면 넘어간다.</li>
 *   <li>매일 학습은 시간이 짧다. 처음 틀린 항목은 설명 없이 그날 끝에 다시 묻는다(스펙 §6.4.8).</li>
 * </ul>
 * 다음 행동은 학습 흐름만 바꾸고 FSRS 등급에는 관여하지 않는다.
 */
public final class NextActionQuestions {

    /** choice: 허용된 행동 중 지금 학습자에게 맞는 다음 행동. */
    public static final String NEXT_ACTION = "next_action";

    private static final Map<FeedbackAction, String> DESCRIPTIONS = Map.of(
            FeedbackAction.ADVANCE, "다음 문제로 넘어간다. 스스로 맞혔거나 힌트만 보고 맞혔고 `repeatedDifficulty`가 거짓일 때, "
                    + "또는 더 할 수 있는 도움이 없을 때 고른다",
            FeedbackAction.RETRY, "도움을 보았으니 같은 문제를 다시 풀게 한다",
            FeedbackAction.GIVE_HINT, "정답을 말하지 않는 힌트를 주고 다시 풀게 한다. 이 문제를 처음 틀렸고 `repeatedDifficulty`가 거짓일 때 고른다",
            FeedbackAction.EXPLAIN_CONCEPT, "개념을 설명한다. `repeatedDifficulty`가 참이거나, 오늘 다시 물을 수 없어 설명 말고는 "
                    + "도울 방법이 없을 때 고른다",
            FeedbackAction.GENERATE_VARIANT, "문제가 모호하므로 변형 문제로 다시 확인한다",
            FeedbackAction.RELEARN_TODAY, "지금은 넘어가고 오늘 풀이 끝에 같은 항목을 한 번 더 묻는다. `practiceKind`가 DAILY인데 처음 틀렸을 때, "
                    + "설명을 보고 맞혔을 때, 도움을 보고 맞혔지만 `repeatedDifficulty`가 참일 때 고른다",
            FeedbackAction.REQUEST_CONFIRMATION, "판정이 불확실하니 사용자에게 확인을 요청한다");

    private NextActionQuestions() {
    }

    /** 선택지 이름은 행동의 소문자 이름이다({@code give_hint}). */
    public static String optionName(FeedbackAction action) {
        return action.name().toLowerCase(Locale.ROOT);
    }

    public static Map<String, JevQuestion> questions(Set<FeedbackAction> allowed) {
        Map<String, String> options = new LinkedHashMap<>();
        for (FeedbackAction action : FeedbackAction.values()) {
            if (allowed.contains(action)) {
                options.put(optionName(action), DESCRIPTIONS.get(action));
            }
        }
        return Map.of(NEXT_ACTION, JevQuestion.choice(
                "`attempts`는 학습자가 문제 `stem`에 낸 시도의 경과(오래된 순)이고 `latestOutcome`은 마지막 시도의 결과다. "
                        + "선택지는 지금 할 수 있는 행동이다. 학습자가 내용을 스스로 떠올려 기억에 남기도록 아래 순서로 판단해 하나를 고른다. "
                        + "(1) `repeatedDifficulty`가 참이면 이 항목을 여러 번 틀려 온 것이다. 틀렸으면 힌트 대신 개념을 설명하고, "
                        + "도움을 보고 맞혔으면 오늘 한 번 더 묻는다. "
                        + "(2) `repeatedDifficulty`가 거짓이고 `latestOutcome`이 WRONG이면: `practiceKind`가 FIRST_STUDY일 때는 스스로 떠올릴 "
                        + "기회를 주기 위해 설명보다 힌트를 먼저 준다. DAILY일 때는 시간이 짧으므로 설명하지 않고 오늘 끝에 다시 묻는다. "
                        + "다시 묻기가 선택지에 없으면 개념을 설명한다. "
                        + "(3) `repeatedDifficulty`가 거짓이고 `latestOutcome`이 CORRECT이면: `explanationShown`이 참이면 설명을 보고 맞힌 것이라 "
                        + "스스로 떠올린 것이 아니므로 오늘 한 번 더 묻는다. `hintShown`만 참이면 다음 문제로 넘어간다.",
                options));
    }
}
