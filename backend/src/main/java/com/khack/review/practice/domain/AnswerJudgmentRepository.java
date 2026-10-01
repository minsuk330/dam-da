package com.khack.review.practice.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerJudgmentRepository extends JpaRepository<AnswerJudgment, Long> {

    Optional<AnswerJudgment> findByAttemptId(Long attemptId);

    List<AnswerJudgment> findByAttemptIdIn(Collection<Long> attemptIds);
}
