package com.khack.review.practice.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionPresentationRepository extends JpaRepository<QuestionPresentation, Long> {

    List<QuestionPresentation> findByUserIdAndMemoryItemId(Long userId, Long memoryItemId);

    Optional<QuestionPresentation> findTopByPracticeSessionIdOrderByPositionDesc(Long practiceSessionId);

    List<QuestionPresentation> findByPracticeSessionIdOrderByPositionAsc(Long practiceSessionId);
}
