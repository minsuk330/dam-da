package com.khack.review.collection.application;

import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.common.domain.AccountDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴한 사용자의 학습 대화를 지운다(#148). */
@Component
class ConversationAccountDeletion {

    private final LearningConversationRepository conversations;

    ConversationAccountDeletion(LearningConversationRepository conversations) {
        this.conversations = conversations;
    }

    @EventListener
    void on(AccountDeleted event) {
        conversations.deleteByUserId(event.userId());
    }
}
