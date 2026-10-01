package com.khack.review.practice.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionPresentationRepository extends JpaRepository<QuestionPresentation, Long> {

    Optional<QuestionPresentation> findTopByPracticeSessionIdOrderByPositionDesc(Long practiceSessionId);

    List<QuestionPresentation> findByPracticeSessionIdOrderByPositionAsc(Long practiceSessionId);
}
