package com.khack.review.practice.application;

import com.khack.review.practice.domain.FirstStudyPlan;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.LearningGoalSet;
import com.khack.review.question.application.QuestionGenerationService;
import com.khack.review.question.domain.QuestionSpec;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** 학습 목표를 저장하고 커밋한 뒤, 첫 학습 계획의 문제 생성을 비동기로 시작한다. */
@Component
class FirstStudyQuestionTrigger {

    private static final Logger log = LoggerFactory.getLogger(FirstStudyQuestionTrigger.class);

    private final FirstStudyPlanRepository plans;
    private final QuestionGenerationService questions;
    private final TransactionTemplate transaction;

    FirstStudyQuestionTrigger(FirstStudyPlanRepository plans, QuestionGenerationService questions, TransactionTemplate transaction) {
        this.plans = plans;
        this.questions = questions;
        this.transaction = transaction;
    }

    @Async
    @TransactionalEventListener
    public void on(LearningGoalSet event) {
        FirstStudyPlan plan = plans.findById(event.planId()).orElseThrow();
        List<QuestionSpec> specs = plan.getQuestions().stream()
                .map(q -> new QuestionSpec(q.getPosition(), q.getMemoryItemId(), q.getRelatedItemId(), q.getQuestionType(),
                        q.getGoal().name()))
                .toList();
        QuestionGenerationService.FirstStudyOutcome outcome;
        try {
            // 목표를 다시 고르면(계획 회차가 바뀌면) 이 생성은 다음 요청 전에 멈추고 결과를 남기지 않는다.
            outcome = questions.generateFirstStudy(event.sessionId(), specs, () -> isCurrent(event));
        } catch (RuntimeException e) {
            log.warn("학습 세션 {} 첫 학습 문제 생성 실패", event.sessionId(), e);
            finish(event, p -> p.failGeneration(event.generation(), "문제 생성 중 오류가 났습니다. 학습 목표를 다시 고르면 다시 만듭니다."));
            return;
        }
        if (!outcome.skipped()) {
            finish(event, p -> p.finishGeneration(event.generation(), outcome.approved(), outcome.failure()));
        }
    }

    private boolean isCurrent(LearningGoalSet event) {
        return plans.findById(event.planId()).map(p -> p.getGeneration() == event.generation()).orElse(false);
    }

    /** 생성 상태를 남긴다. 그 사이 목표를 다시 골랐으면 새 계획을 건드리지 않는다. */
    private void finish(LearningGoalSet event, Predicate<FirstStudyPlan> update) {
        transaction.executeWithoutResult(tx -> plans.findById(event.planId()).filter(update)
                .ifPresent(p -> log.info("학습 세션 {} 첫 학습 문제 생성 {}", event.sessionId(), p.getGenerationStatus())));
    }
}
