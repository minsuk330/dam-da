package com.khack.review.collection.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningConversationRepository extends JpaRepository<LearningConversation, Long> {

    List<LearningConversation> findAllByOrderByReceivedAtAscIdAsc();
}
