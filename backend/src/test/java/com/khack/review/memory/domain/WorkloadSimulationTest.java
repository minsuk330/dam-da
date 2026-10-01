package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkloadSimulationTest {

    static WorkloadSimulation.Estimate estimate(MemoryStrength strength, int items) {
        return WorkloadSimulation.estimate(
                FsrsSchedulers.create(FsrsSchedulers.defaultParameters(), strength.desiredRetention()), items, 30, 0.75);
    }

    @Test
    void strongerMemoryMeansMoreReviews() {
        WorkloadSimulation.Estimate light = estimate(MemoryStrength.LIGHT, 10);
        WorkloadSimulation.Estimate master = estimate(MemoryStrength.MASTER, 10);

        assertThat(light.reviews()).isGreaterThanOrEqualTo(10);
        assertThat(master.reviews()).isGreaterThan(light.reviews());
        assertThat(master.dailyMinutes()).isGreaterThan(light.dailyMinutes());
    }

    @Test
    void minutesFollowReviewCount() {
        WorkloadSimulation.Estimate estimate = estimate(MemoryStrength.APPLY, 4);

        assertThat(estimate.dailyMinutes()).isEqualTo(estimate.reviews() * 0.75 / 30);
        assertThat(estimate(MemoryStrength.APPLY, 0).reviews()).isZero();
    }

    @Test
    void strengthTableMatchesTheSpec() {
        assertThat(MemoryStrength.LIGHT.desiredRetention()).isEqualTo(0.80);
        assertThat(MemoryStrength.UNDERSTAND.desiredRetention()).isEqualTo(0.85);
        assertThat(MemoryStrength.APPLY.desiredRetention()).isEqualTo(0.90);
        assertThat(MemoryStrength.MASTER.desiredRetention()).isEqualTo(0.95);
        assertThat(MemoryStrength.UNDERSTAND.maxQuestionLevel()).isEqualTo(2);
        assertThat(MemoryStrength.APPLY.maxQuestionLevel()).isEqualTo(3);
    }
}
