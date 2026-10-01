package com.khack.review.question.domain;

import java.util.List;

/**
 * 문제 하나가 겨냥하는 기억 항목과 유형. 문제는 항상 기억 항목 하나를 대상으로 한다(스펙 §6.4.1).
 * {@code unitIndex}·{@code itemIndex}는 {@link QuestionSource} 안의 0부터 시작하는 위치다.
 */
public record QuestionTarget(
        LearningGoal goal,
        QuestionType type,
        int unitIndex,
        int itemIndex,
        ItemKind itemKind,
        List<Integer> evidenceTurns) {
}
