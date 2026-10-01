package com.khack.review.collection.application;

import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 학습 대화 조회. 오래된 것부터 돌려준다. */
@Service
public class ConversationQueryService {

    private final LearningConversationRepository conversations;

    public ConversationQueryService(LearningConversationRepository conversations) {
        this.conversations = conversations;
    }

    @Transactional(readOnly = true)
    public List<SavedSession> list() {
        return conversations.findAllByOrderByReceivedAtAscIdAsc().stream()
                .map(LearningConversation::toSavedSession)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SavedSession> find(String sessionId) {
        return conversations.findBySessionId(sessionId).map(LearningConversation::toSavedSession);
    }
}
