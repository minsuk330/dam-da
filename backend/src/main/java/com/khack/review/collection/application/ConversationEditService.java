package com.khack.review.collection.application;

import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확인 화면에서 사용자가 발화를 고치거나 빠진 발화를 넣는다(스펙 §7.3 대응 3, 규칙 13). 원문 검증의 최종 주체는 사용자다.
 * 언제 고칠 수 있는지는 분석 컨텍스트(학습 세션 상태)가 정한다.
 */
@Service
public class ConversationEditService {

    private final LearningConversationRepository conversations;

    public ConversationEditService(LearningConversationRepository conversations) {
        this.conversations = conversations;
    }

    @Transactional
    public void editTurn(Long conversationId, int index, TurnContent content) {
        conversation(conversationId).editTurn(index, content.text(), content.intent(), content.aiVerdict(), content.correction());
    }

    /** 넣은 발화의 index를 돌려준다. 그 뒤 발화의 index는 1씩 밀린다. */
    @Transactional
    public int insertTurnAfter(Long conversationId, int afterIndex, TurnContent content) {
        return conversation(conversationId).insertTurnAfter(afterIndex, content.text(), content.intent(),
                content.aiVerdict(), content.correction());
    }

    private LearningConversation conversation(Long conversationId) {
        return conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("학습 대화 없음: " + conversationId));
    }
}
