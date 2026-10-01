package com.khack.review.question.application;

import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.QuestionPlan;
import com.khack.review.question.domain.QuestionTarget;
import java.util.List;
import java.util.Map;

/**
 * 문제 생성 결과. 문제 내용은 담지 않는다. 후보는 품질 검사를 통과하기 전에 사용자에게 보여줄 수 없기 때문이다.
 *
 * @param candidateIds 저장한 문제 후보 ID (출제 순서)
 * @param deferred     첫 학습 상한을 넘겨 문제를 만들지 않은 대상
 * @param emptyGoals   0문제가 된 목표 → 앱에 보여줄 이유
 * @param skipped      근거가 없어 제외한 기억 항목
 * @param failures     LLM이 형식에 맞는 문제를 만들지 못한 대상
 */
public record QuestionGenerationResult(
        List<Long> candidateIds,
        List<QuestionTarget> deferred,
        Map<LearningGoal, String> emptyGoals,
        List<QuestionPlan.Skipped> skipped,
        List<QuestionDrafter.Failure> failures) {
}
