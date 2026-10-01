package com.khack.review.practice.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresentationFeedbackRepository extends JpaRepository<PresentationFeedback, Long> {

    Optional<PresentationFeedback> findByPresentationId(Long presentationId);

    /** 이 항목에서 한 번이라도 틀린 제시 수 (반복 어려움 판단). */
    long countByUserIdAndMemoryItemIdAndWrongAttemptsGreaterThan(Long userId, Long memoryItemId, int wrongAttempts);
}
