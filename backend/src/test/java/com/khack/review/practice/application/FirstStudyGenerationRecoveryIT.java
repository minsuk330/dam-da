package com.khack.review.practice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.practice.domain.FirstStudyPlan;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.GenerationStatus;
import com.khack.review.practice.domain.LearningGoal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 서버가 생성 도중 멈춰 생성 중으로 남은 계획은 서버가 뜰 때 실패로 바뀐다. 끝난 계획은 건드리지 않는다. */
@SpringBootTest
class FirstStudyGenerationRecoveryIT {

    @Autowired
    FirstStudyPlanRepository plans;

    @Autowired
    FirstStudyGenerationRecovery recovery;

    private FirstStudyPlan plan(long sessionId) {
        FirstStudyPlan plan = new FirstStudyPlan(1L, sessionId);
        plan.replace(List.of(LearningGoal.KEY_RECALL), List.of(), Instant.parse("2026-10-02T09:00:00Z"));
        return plan;
    }

    @Test
    void interruptedGenerationsBecomeFailedAndFinishedOnesStay() {
        long stuck = plans.save(plan(900_001L)).getId();
        FirstStudyPlan finished = plan(900_002L);
        finished.finishGeneration(finished.getGeneration(), 2, null);
        long ready = plans.save(finished).getId();

        recovery.failInterruptedGenerations();

        assertThat(plans.findById(stuck)).hasValueSatisfying(p -> {
            assertThat(p.getGenerationStatus()).isEqualTo(GenerationStatus.FAILED);
            assertThat(p.getGenerationFailure()).isEqualTo(FirstStudyGenerationRecovery.INTERRUPTED);
        });
        assertThat(plans.findById(ready).orElseThrow().getGenerationStatus()).isEqualTo(GenerationStatus.READY);
    }
}
