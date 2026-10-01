package com.khack.review.question.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySessionId(Long sessionId);

    List<Question> findBySessionIdAndStatusOrderById(Long sessionId, QuestionStatus status);
}
