package com.khack.review.question.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySessionIdOrderByIdAsc(Long sessionId);

    List<Question> findBySessionIdAndStatusOrderByPlanPositionAscIdAsc(Long sessionId, QuestionStatus status);

    List<Question> findByMemoryItemIdOrderByIdAsc(Long memoryItemId);

    List<Question> findByMemoryItemIdAndStatusOrderByIdAsc(Long memoryItemId, QuestionStatus status);

    void deleteByUserId(Long userId);
}
