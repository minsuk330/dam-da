package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 복습 단위 검수는 비동기라 서버가 도중에 멈추면 세션이 접수·검수 중에 남고, 사용자는 확인 단계로 넘어가지 못한다(#189).
 * 서버가 뜰 때 그런 세션을 다시 검수한다. 비동기로 하나씩 돌아 서버 시작을 막지 않는다.
 */
@Component
class UnitReviewRecovery {

    private static final Logger log = LoggerFactory.getLogger(UnitReviewRecovery.class);

    private final LearningSessionRepository sessions;
    private final UnitReviewService reviews;
    private final TransactionTemplate transaction;

    UnitReviewRecovery(LearningSessionRepository sessions, UnitReviewService reviews, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.reviews = reviews;
        this.transaction = transaction;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterrupted() {
        List<Long> unfinished = sessions.findIdsByStatusIn(List.of(LearningSessionStatus.RECEIVED, LearningSessionStatus.REVIEWING));
        if (unfinished.isEmpty()) {
            return;
        }
        log.info("검수가 끝나지 않은 학습 세션 {}개를 다시 검수함", unfinished.size());
        for (Long sessionId : unfinished) {
            try {
                transaction.executeWithoutResult(status -> sessions.findById(sessionId).ifPresent(LearningSession::resumeInterruptedReview));
                reviews.review(sessionId);
            } catch (RuntimeException e) {
                log.warn("학습 세션 {} 검수 재시작 실패: {}", sessionId, e.getMessage());
            }
        }
    }
}
