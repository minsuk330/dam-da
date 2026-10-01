package com.khack.review.practice.application;

import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.practice.domain.FeedbackAction;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 다음 행동 선택 Jev 질문 정의 (스펙 §6.2, docs/jev.md). 질문 문장·선택지 설명은 프롬프트이므로 ai 소유다(지금은 자리표시 문구).
 * 선택지는 상태 기계({@code FeedbackRules})가 허용한 행동만 담는다. 판정 서비스({@link NextActionJudge})는 질문 이름과 선택지 이름에만 의존한다.
 */
public final class NextActionQuestions {

    /** choice: 허용된 행동 중 지금 학습자에게 맞는 다음 행동. */
    public static final String NEXT_ACTION = "next_action";

    private static final Map<FeedbackAction, String> DESCRIPTIONS = Map.of(
            FeedbackAction.ADVANCE, "다음 문제로 넘어간다",
            FeedbackAction.RETRY, "도움을 보았으니 같은 문제를 다시 풀게 한다",
            FeedbackAction.GIVE_HINT, "정답을 말하지 않는 힌트를 준다",
            FeedbackAction.EXPLAIN_CONCEPT, "개념을 설명한다",
            FeedbackAction.GENERATE_VARIANT, "문제가 모호하므로 변형 문제로 다시 확인한다",
            FeedbackAction.RELEARN_TODAY, "오늘 안에 같은 항목을 한 번 더 묻는다",
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
                "`attempts`는 학습자가 문제 `stem`을 풀며 낸 시도의 경과다. `latestOutcome`과 `hintShown`·`explanationShown`·"
                        + "`repeatedDifficulty`를 보고, 이 학습자가 기억에 남기려면 지금 어떤 행동이 가장 알맞은가?",
                options));
    }
}
