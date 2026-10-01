package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.collection.domain.ConversationSubmitted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 학습 대화로 학습 세션·복습 단위·기억 항목을 만든다. 대화 저장과 같은 트랜잭션에서 실행되어,
 * 세션을 만들 수 없으면 대화 저장도 함께 취소된다.
 */
@Service
public class LearningSessionCreator {

    private final LearningSessionRepository sessions;

    public LearningSessionCreator(LearningSessionRepository sessions) {
        this.sessions = sessions;
    }

    @EventListener
    @Transactional
    public void on(ConversationSubmitted event) {
        sessions.save(LearningSession.create(event.userId(), event.conversationId(), event.input(), event.receivedAt()));
    }
}
