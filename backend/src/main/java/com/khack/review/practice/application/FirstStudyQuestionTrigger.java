package com.khack.review.practice.application;

import com.khack.review.practice.domain.FirstStudyPlan;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.LearningGoalSet;
import com.khack.review.question.application.QuestionGenerationService;
import com.khack.review.question.domain.QuestionSpec;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** 학습 목표를 저장하고 커밋한 뒤, 첫 학습 계획의 문제 생성을 비동기로 시작한다. */
@Component
class FirstStudyQuestionTrigger {

    private final FirstStudyPlanRepository plans;
    private final QuestionGenerationService questions;

    FirstStudyQuestionTrigger(FirstStudyPlanRepository plans, QuestionGenerationService questions) {
        this.plans = plans;
        this.questions = questions;
    }

    @Async
    @TransactionalEventListener
    public void on(LearningGoalSet event) {
        FirstStudyPlan plan = plans.findById(event.planId()).orElseThrow();
        List<QuestionSpec> specs = plan.getQuestions().stream()
                .map(q -> new QuestionSpec(q.getPosition(), q.getMemoryItemId(), q.getRelatedItemId(), q.getQuestionType(),
                        q.getGoal().name()))
                .toList();
        questions.generateFirstStudy(event.sessionId(), specs);
    }
}
