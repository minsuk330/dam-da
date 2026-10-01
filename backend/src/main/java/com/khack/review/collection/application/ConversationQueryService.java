package com.khack.review.collection.application;

import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 학습 대화 조회. 오래된 것부터 돌려준다. */
@Service
public class ConversationQueryService {

    private final LearningConversationRepository conversations;

    public ConversationQueryService(LearningConversationRepository conversations) {
        this.conversations = conversations;
    }

    /** 다른 컨텍스트가 근거 발화를 읽을 때 쓴다. 없으면 {@link IllegalArgumentException}. */
    @Transactional(readOnly = true)
    public ConversationEvidence evidence(Long conversationId) {
        LearningConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("학습 대화 없음: " + conversationId));
        return new ConversationEvidence(conversation.getFidelity(), conversation.userTurns());
    }

    @Transactional(readOnly = true)
    public List<SavedSession> list() {
        return conversations.findAllByOrderByReceivedAtAscIdAsc().stream()
                .map(LearningConversation::toSavedSession)
                .toList();
    }
}
