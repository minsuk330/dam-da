package com.khack.review.practice.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirstStudyPlanRepository extends JpaRepository<FirstStudyPlan, Long> {

    Optional<FirstStudyPlan> findBySessionId(Long sessionId);

    List<FirstStudyPlan> findByGenerationStatus(GenerationStatus status);
}
