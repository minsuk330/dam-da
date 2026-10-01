package com.khack.review.question.domain;

import java.util.List;
import java.util.Map;

/**
 * 첫 학습 출제 계획.
 *
 * @param targets    이번에 문제를 만들 대상. 헷갈린 지점이 먼저 온다
 * @param deferred   첫 학습 상한을 넘겨 신규 항목으로 남기는 대상(스펙 §6.4.4)
 * @param emptyGoals 맞는 항목이 없어 0문제가 된 목표 → 앱에 보여줄 이유
 * @param skipped    근거가 없어 문제를 만들 수 없는 기억 항목
 */
public record QuestionPlan(
        List<QuestionTarget> targets,
        List<QuestionTarget> deferred,
        Map<LearningGoal, String> emptyGoals,
        List<Skipped> skipped) {

    public record Skipped(int unitIndex, int itemIndex, String reason) {
    }
}
