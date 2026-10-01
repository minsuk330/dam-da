package com.khack.review.collection.application;

import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.common.application.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장된 학습 대화 조회. 오래된 것부터 돌려준다. */
@Service
public class ConversationQueryService {

    private final LearningConversationRepository conversations;
    private final CurrentUser currentUser;

    public ConversationQueryService(LearningConversationRepository conversations, CurrentUser currentUser) {
        this.conversations = conversations;
        this.currentUser = currentUser;
    }

    /** 다른 컨텍스트가 근거 발화를 읽을 때 쓴다. 없으면 {@link IllegalArgumentException}. */
    @Transactional(readOnly = true)
    public ConversationEvidence evidence(Long conversationId) {
        LearningConversation conversation = conversation(conversationId);
        return new ConversationEvidence(conversation.getFidelity(), conversation.userTurns());
    }

    /** 다른 컨텍스트가 대화 1건을 보여줄 때 쓴다. 없으면 {@link IllegalArgumentException}. */
    @Transactional(readOnly = true)
    public SavedSession find(Long conversationId) {
        return conversation(conversationId).toSavedSession();
    }

    /** 지금 사용자의 대화. 앱의 받은 대화 화면이 쓴다. */
    @Transactional(readOnly = true)
    public List<SavedSession> mine() {
        return conversations.findAllByUserIdOrderByReceivedAtAscIdAsc(currentUser.id()).stream()
                .map(LearningConversation::toSavedSession)
                .toList();
    }

    /** 개발 도구 전용: 모든 사용자의 대화(세션 뷰어, 벤치마크). 앱 API에서 쓰지 않는다. */
    @Transactional(readOnly = true)
    public List<SavedSession> all() {
        return conversations.findAllByOrderByReceivedAtAscIdAsc().stream()
                .map(LearningConversation::toSavedSession)
                .toList();
    }

    /** 외부 ID(sessionId)로 지금 사용자의 대화를 찾는다. 남의 대화는 없는 것과 같다. */
    @Transactional(readOnly = true)
    public Optional<SavedSession> findMineBySessionId(String sessionId) {
        return conversations.findBySessionIdAndUserId(sessionId, currentUser.id()).map(LearningConversation::toSavedSession);
    }

    /** 외부 ID(sessionId)의 내부 대화 ID. 다른 컨텍스트는 내부 ID로 참조한다. */
    @Transactional(readOnly = true)
    public Optional<Long> idOf(String sessionId) {
        return conversations.findBySessionId(sessionId).map(LearningConversation::getId);
    }

    private LearningConversation conversation(Long conversationId) {
        return conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("학습 대화 없음: " + conversationId));
    }
}
