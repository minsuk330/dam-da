package com.khack.review.practice.domain;

import java.util.List;

/**
 * 학습 목표를 고르고 첫 학습 문제를 계획했다(스펙 §8.4 `LearningGoalSet`). 계획 저장과 같은 트랜잭션에서 발행된다.
 * 문제 생성(#15)이 이 이벤트로 시작한다.
 */
public record LearningGoalSet(Long sessionId, Long userId, Long planId, List<LearningGoal> goals, int plannedQuestions) {
}
