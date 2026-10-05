package com.khack.review.practice.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRecheckRepository extends JpaRepository<QuestionRecheck, Long> {

    Optional<QuestionRecheck> findByQuestionId(Long questionId);

    void deleteByUserId(Long userId);
}
