package com.khack.review.practice.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {

    List<PracticeAttempt> findByPresentationIdOrderBySubmittedAtAscIdAsc(Long presentationId);

    boolean existsByPresentationId(Long presentationId);

    @Query("select a.id from PracticeAttempt a where a.userId = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
