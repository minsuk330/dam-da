package com.khack.review.analysis.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {

    Optional<LearningSession> findByConversationId(Long conversationId);

    List<LearningSession> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
