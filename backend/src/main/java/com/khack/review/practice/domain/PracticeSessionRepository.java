package com.khack.review.practice.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    Optional<PracticeSession> findFirstByLearningSessionIdAndKindOrderByIdAsc(Long learningSessionId, PracticeKind kind);
}
