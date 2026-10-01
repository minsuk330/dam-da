package com.khack.review.practice.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {

    List<PracticeAttempt> findByPresentationIdOrderBySubmittedAtAscIdAsc(Long presentationId);

    boolean existsByPresentationId(Long presentationId);
}
