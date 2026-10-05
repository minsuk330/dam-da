package com.khack.review.practice.application;

import com.khack.review.common.domain.AccountDeleted;
import com.khack.review.practice.domain.AidExposureRepository;
import com.khack.review.practice.domain.AnswerJudgmentRepository;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.PracticeAttemptRepository;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.practice.domain.PresentationFeedbackRepository;
import com.khack.review.practice.domain.QuestionPresentationRepository;
import com.khack.review.practice.domain.QuestionRecheckRepository;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴한 사용자의 풀이·판정·도움 노출·피드백·재확인·첫 학습 계획을 지운다(#148). */
@Component
class PracticeAccountDeletion {

    private final PracticeSessionRepository practices;
    private final QuestionPresentationRepository presentations;
    private final PracticeAttemptRepository attempts;
    private final AnswerJudgmentRepository judgments;
    private final AidExposureRepository aids;
    private final PresentationFeedbackRepository feedback;
    private final QuestionRecheckRepository rechecks;
    private final FirstStudyPlanRepository plans;

    PracticeAccountDeletion(PracticeSessionRepository practices, QuestionPresentationRepository presentations,
            PracticeAttemptRepository attempts, AnswerJudgmentRepository judgments, AidExposureRepository aids,
            PresentationFeedbackRepository feedback, QuestionRecheckRepository rechecks, FirstStudyPlanRepository plans) {
        this.practices = practices;
        this.presentations = presentations;
        this.attempts = attempts;
        this.judgments = judgments;
        this.aids = aids;
        this.feedback = feedback;
        this.rechecks = rechecks;
        this.plans = plans;
    }

    @EventListener
    void on(AccountDeleted event) {
        Long userId = event.userId();
        List<Long> attemptIds = attempts.findIdsByUserId(userId);
        if (!attemptIds.isEmpty()) {
            judgments.deleteByAttemptIdIn(attemptIds);
        }
        List<Long> presentationIds = presentations.findIdsByUserId(userId);
        if (!presentationIds.isEmpty()) {
            aids.deleteByPresentationIdIn(presentationIds);
        }
        attempts.deleteByUserId(userId);
        feedback.deleteByUserId(userId);
        rechecks.deleteByUserId(userId);
        presentations.deleteByUserId(userId);
        practices.deleteByUserId(userId);
        plans.deleteByUserId(userId);
    }
}
