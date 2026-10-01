package com.khack.review.analysis.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LearningSessionRepository extends JpaRepository<LearningSession, Long> {

    Optional<LearningSession> findByConversationId(Long conversationId);

    List<LearningSession> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 학습 분야 라벨이 아직 없는 세션 (스펙 §7.10). */
    @Query("select s.id from LearningSession s where not exists (select 1 from SessionField f where f.sessionId = s.id) order by s.id")
    List<Long> findIdsWithoutField();
}
