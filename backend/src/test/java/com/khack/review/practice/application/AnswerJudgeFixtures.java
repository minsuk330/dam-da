package com.khack.review.practice.application;

import com.khack.review.common.application.port.out.JevAnswer;
import java.util.Map;

/** 답변 판정 테스트용 Jev 답. */
public final class AnswerJudgeFixtures {

    private AnswerJudgeFixtures() {
    }

    /**
     * `misread` 질문(choice)의 답. {@code misreadConfidence}가 0.5 이상이면 질문을 오독했다고 그 신뢰도로 고른 것이고,
     * 미만이면 질문에 답했다고 {@code 1 - misreadConfidence}의 신뢰도로 고른 것이다.
     */
    public static JevAnswer.Choice misread(double misreadConfidence) {
        if (misreadConfidence >= 0.5) {
            return new JevAnswer.Choice(AnswerJudgeQuestions.MISREAD_CHOICE, Map.of(AnswerJudgeQuestions.MISREAD_CHOICE, misreadConfidence),
                    misreadConfidence);
        }
        return new JevAnswer.Choice(AnswerJudgeQuestions.AS_ASKED, Map.of(AnswerJudgeQuestions.MISREAD_CHOICE, misreadConfidence),
                1 - misreadConfidence);
    }
}
