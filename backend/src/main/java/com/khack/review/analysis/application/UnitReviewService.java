package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionCreated;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.ReviewUnit;
import com.khack.review.analysis.domain.LearningSessionReadyForConfirmation;
import com.khack.review.analysis.domain.ReviewUnitApproved;
import com.khack.review.analysis.domain.ReviewUnitVerdict;
import com.khack.review.collection.application.ConversationEvidence;
import com.khack.review.collection.application.ConversationQueryService;
import com.khack.review.collection.domain.Fidelity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 학습 세션의 복습 단위를 Jev로 검수한다 (스펙 §7.1, 도메인 스토리 S1-6). 세션 저장이 커밋된 뒤 비동기로 실행되어
 * 커넥터 응답을 막지 않는다. Jev 호출은 트랜잭션 밖에서 하고, 끝나면 세션을 확인 대기로 넘긴다.
 * 판정은 기록만 하고 복습 단위를 자동으로 빼지 않는다.
 */
@Service
public class UnitReviewService {

    private static final Logger log = LoggerFactory.getLogger(UnitReviewService.class);

    private final LearningSessionRepository sessions;
    private final ConversationQueryService conversations;
    private final UnitReviewJudge judge;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    public UnitReviewService(LearningSessionRepository sessions, ConversationQueryService conversations,
            UnitReviewJudge judge, ApplicationEventPublisher events, TransactionTemplate transaction) {
        this.sessions = sessions;
        this.conversations = conversations;
        this.judge = judge;
        this.events = events;
        this.transaction = transaction;
    }

    @Async
    @TransactionalEventListener
    public void on(LearningSessionCreated event) {
        review(event.sessionId());
    }

    /** 접수 상태의 세션만 검수한다. 이미 검수를 시작했으면 아무것도 하지 않는다. */
    public void review(Long sessionId) {
        Plan plan = transaction.execute(status -> start(sessionId));
        if (plan == null) {
            return;
        }
        Map<Long, UnitReviewOutcome> outcomes = new LinkedHashMap<>();
        plan.states().forEach((unitId, state) -> outcomes.put(unitId, judge.judge(state, plan.fidelity())));
        transaction.executeWithoutResult(status -> finish(sessionId, outcomes));
    }

    private @Nullable Plan start(Long sessionId) {
        LearningSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션 없음: " + sessionId));
        if (session.getStatus() != LearningSessionStatus.RECEIVED) {
            log.info("학습 세션 {}: 이미 {} 상태라 검수하지 않음", sessionId, session.getStatus());
            return null;
        }
        session.moveTo(LearningSessionStatus.REVIEWING);
        ConversationEvidence evidence = conversations.evidence(session.getConversationId());
        Map<Long, UnitReviewState> states = new LinkedHashMap<>();
        for (ReviewUnit unit : session.getUnits()) {
            states.put(unit.getId(), UnitReviewState.of(session.getTopicHint(), unit, evidence.turns()));
        }
        return new Plan(evidence.fidelity(), states);
    }

    private void finish(Long sessionId, Map<Long, UnitReviewOutcome> outcomes) {
        LearningSession session = sessions.findById(sessionId).orElseThrow();
        for (ReviewUnit unit : session.getUnits()) {
            UnitReviewOutcome outcome = outcomes.get(unit.getId());
            if (outcome == null) {
                continue;
            }
            unit.recordVerdict(outcome.verdict(), outcome.reason());
            if (outcome.verdict() == ReviewUnitVerdict.APPROVED) {
                events.publishEvent(new ReviewUnitApproved(sessionId, unit.getId()));
            }
        }
        session.moveTo(LearningSessionStatus.AWAITING_CONFIRMATION);
        events.publishEvent(new LearningSessionReadyForConfirmation(sessionId, session.getUserId(), session.getTopicHint()));
        log.info("학습 세션 {} 검수 완료: {}", sessionId, outcomes.values().stream().map(UnitReviewOutcome::verdict).toList());
    }

    private record Plan(Fidelity fidelity, Map<Long, UnitReviewState> states) {
    }
}
