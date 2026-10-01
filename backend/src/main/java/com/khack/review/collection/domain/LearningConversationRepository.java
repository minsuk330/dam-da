package com.khack.review.collection.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningConversationRepository extends JpaRepository<LearningConversation, Long> {

    List<LearningConversation> findAllByOrderByReceivedAtAscIdAsc();

    List<LearningConversation> findAllByUserIdOrderByReceivedAtAscIdAsc(Long userId);

    Optional<LearningConversation> findBySessionIdAndUserId(String sessionId, Long userId);

    Optional<LearningConversation> findBySessionId(String sessionId);
}
