package com.khack.review.practice.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionPresentationRepository extends JpaRepository<QuestionPresentation, Long> {

    List<QuestionPresentation> findByUserIdAndMemoryItemId(Long userId, Long memoryItemId);

    Optional<QuestionPresentation> findTopByPracticeSessionIdOrderByPositionDesc(Long practiceSessionId);

    List<QuestionPresentation> findByPracticeSessionIdOrderByPositionAsc(Long practiceSessionId);

    void deleteByUserId(Long userId);

    @Query("select p.id from QuestionPresentation p where p.userId = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);
}
