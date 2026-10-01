package com.khack.review.memory.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {

    Optional<ReviewLog> findByAttemptId(Long attemptId);

    List<ReviewLog> findByUserIdAndRatingIsNotNullOrderByReviewedAtAscIdAsc(Long userId);

    List<ReviewLog> findByMemoryItemIdOrderByReviewedAtAscIdAsc(Long memoryItemId);
}
