package com.khack.review.memory.domain;

import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;
import java.time.Instant;

/**
 * 기억 강도를 고를 때 보여줄 예상 하루 부담 (스펙 §6.4.7). 새 항목들을 오늘 처음 풀고, 이후 복습 시점마다
 * 모두 맞힌다(Good)고 가정해 정해진 기간의 복습 횟수를 센다. 틀리는 복습이 있으면 실제 부담은 더 크다.
 */
public final class WorkloadSimulation {

    private WorkloadSimulation() {
    }

    /** 기간 안의 복습 횟수(첫 풀이 포함)와 하루 평균 분. */
    public record Estimate(int reviews, double dailyMinutes) {
    }

    public static Estimate estimate(Scheduler scheduler, int itemCount, int days, double minutesPerReview) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = start.plus(Duration.ofDays(days));
        MemoryState state = new MemoryState(1L, 1L, scheduler.getDesiredRetention(), start);
        int perItem = 0;
        Instant at = start;
        while (at.isBefore(end)) {
            state.review(scheduler, 0, Rating.GOOD, at);
            perItem++;
            at = state.getDue();
        }
        int reviews = perItem * itemCount;
        return new Estimate(reviews, reviews * minutesPerReview / days);
    }
}
