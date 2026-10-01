package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.ReviewUnitsConfirmedByUser;
import com.khack.review.collection.application.ConversationEditService;
import com.khack.review.collection.application.TurnContent;
import com.khack.review.collection.domain.Intent;
import java.time.Clock;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확인 화면에서 사용자가 복습 단위·기억 항목을 빼고, 발화를 고치거나 빠진 발화를 넣고, 확인을 마친다 (도메인 스토리 S1-8, 규칙 9·12·13).
 * 확인 대기 중에만 고칠 수 있다. 고칠 때마다 재검증한 상세를 돌려준다.
 */
@Service
@Transactional
public class SessionConfirmationService {

    private final LearningSessionQueryService query;
    private final ConversationEditService conversations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SessionConfirmationService(LearningSessionQueryService query, ConversationEditService conversations,
            ApplicationEventPublisher events, Clock clock) {
        this.query = query;
        this.conversations = conversations;
        this.events = events;
        this.clock = clock;
    }

    public LearningSessionDetail setUnitExcluded(Long sessionId, Long unitId, boolean excluded) {
        query.owned(sessionId).setUnitExcluded(unitId, excluded);
        return query.detail(sessionId);
    }

    public LearningSessionDetail setItemExcluded(Long sessionId, Long itemId, boolean excluded) {
        query.owned(sessionId).setItemExcluded(itemId, excluded);
        return query.detail(sessionId);
    }

    public LearningSessionDetail editTurn(Long sessionId, int index, TurnContent content) {
        LearningSession session = query.owned(sessionId);
        session.requireEditable();
        conversations.editTurn(session.getConversationId(), index, content);
        if (content.intent() == Intent.meta) {
            session.turnBecameMeta(index);
        }
        return query.detail(sessionId);
    }

    /** {@code sourceOf}는 새 발화를 출처로 더할 기억 항목 ID. `meta` 발화는 출처가 될 수 없다(규칙 15). */
    public LearningSessionDetail insertTurn(Long sessionId, int afterIndex, TurnContent content, List<Long> sourceOf) {
        LearningSession session = query.owned(sessionId);
        session.requireEditable();
        if (content.intent() == Intent.meta && !sourceOf.isEmpty()) {
            throw new IllegalArgumentException("meta 발화는 기억 항목의 출처가 될 수 없습니다.");
        }
        int index = conversations.insertTurnAfter(session.getConversationId(), afterIndex, content);
        session.turnInserted(index, sourceOf);
        return query.detail(sessionId);
    }

    public LearningSessionDetail confirm(Long sessionId) {
        query.owned(sessionId).confirm(clock.instant());
        events.publishEvent(new ReviewUnitsConfirmedByUser(sessionId));
        return query.detail(sessionId);
    }
}
