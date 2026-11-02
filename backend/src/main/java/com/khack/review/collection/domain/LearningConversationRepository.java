package com.khack.review.collection.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LearningConversationRepository extends JpaRepository<LearningConversation, Long> {

    List<LearningConversation> findAllByOrderByReceivedAtAscIdAsc();

    List<LearningConversation> findAllByUserIdOrderByReceivedAtAscIdAsc(Long userId);

    Optional<LearningConversation> findBySessionIdAndUserId(String sessionId, Long userId);

    Optional<LearningConversation> findBySessionId(String sessionId);

    void deleteByUserId(Long userId);

    /** 대화를 한 번이라도 저장한 사용자 (#182 사용 지표). */
    @Query("select distinct c.userId from LearningConversation c")
    List<Long> findDistinctUserIds();
}
