package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionCreated;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.collection.domain.ConversationSubmitted;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 학습 대화로 학습 세션·복습 단위·기억 항목을 만든다. 대화 저장과 같은 트랜잭션에서 실행되어,
 * 세션을 만들 수 없으면 대화 저장도 함께 취소된다. 만든 뒤 {@link LearningSessionCreated}를 발행해 검수를 넘긴다.
 */
@Service
public class LearningSessionCreator {

    private final LearningSessionRepository sessions;
    private final ApplicationEventPublisher events;

    public LearningSessionCreator(LearningSessionRepository sessions, ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void on(ConversationSubmitted event) {
        LearningSession session = sessions.save(
                LearningSession.create(event.userId(), event.conversationId(), event.input(), event.receivedAt()));
        events.publishEvent(new LearningSessionCreated(session.getId()));
    }
}
