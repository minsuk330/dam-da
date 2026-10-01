package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryStateTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    static final Scheduler SCHEDULER = FsrsSchedulers.create(FsrsSchedulers.defaultParameters(), 0.9);

    static MemoryState reviewed(Rating rating, Scheduler scheduler) {
        MemoryState state = new MemoryState(1L, 10L, 0.9, NOW);
        state.review(scheduler, 1, rating, NOW);
        return state;
    }

    @Test
    void firstRatingGoesStraightToReviewWithADayOrMoreBecauseStepsAreOff() {
        MemoryState state = reviewed(Rating.GOOD, SCHEDULER);

        assertThat(state.getState()).isEqualTo(State.REVIEW);
        assertThat(state.getStability()).isPositive();
        assertThat(state.getLastReview()).isEqualTo(NOW);
        assertThat(Duration.between(NOW, state.getDue())).isGreaterThanOrEqualTo(Duration.ofDays(1));
    }

    @Test
    void betterRatingsGiveLongerIntervals() {
        Instant again = reviewed(Rating.AGAIN, SCHEDULER).getDue();
        Instant hard = reviewed(Rating.HARD, SCHEDULER).getDue();
        Instant good = reviewed(Rating.GOOD, SCHEDULER).getDue();
        Instant easy = reviewed(Rating.EASY, SCHEDULER).getDue();

        assertThat(again).isBeforeOrEqualTo(hard);
        assertThat(hard).isBeforeOrEqualTo(good);
        assertThat(good).isBefore(easy);
        assertThat(again).isBefore(easy);
    }

    @Test
    void sameInputGivesTheSameScheduleBecauseFuzzIsOff() {
        MemoryState first = reviewed(Rating.GOOD, SCHEDULER);
        first.review(SCHEDULER, 1, Rating.GOOD, first.getDue());
        MemoryState second = reviewed(Rating.GOOD, SCHEDULER);
        second.review(SCHEDULER, 1, Rating.GOOD, second.getDue());

        assertThat(second.getDue()).isEqualTo(first.getDue());
    }

    @Test
    void retrievabilityIsEmptyBeforeTheFirstRatingAndFallsOverTime() {
        MemoryState state = new MemoryState(1L, 10L, 0.9, NOW);
        assertThat(state.retrievability(SCHEDULER, NOW)).isEmpty();

        state.review(SCHEDULER, 1, Rating.GOOD, NOW);

        double justAfter = state.retrievability(SCHEDULER, NOW).orElseThrow();
        double monthLater = state.retrievability(SCHEDULER, NOW.plus(Duration.ofDays(30))).orElseThrow();
        assertThat(justAfter).isGreaterThan(0.99);
        assertThat(monthLater).isLessThan(justAfter);
    }

    @Test
    void higherDesiredRetentionSchedulesSooner() {
        Instant loose = reviewed(Rating.GOOD, FsrsSchedulers.create(FsrsSchedulers.defaultParameters(), 0.80)).getDue();
        Instant strict = reviewed(Rating.GOOD, FsrsSchedulers.create(FsrsSchedulers.defaultParameters(), 0.95)).getDue();

        assertThat(strict).isBefore(loose);
    }

    @Test
    void parametersMustHaveTwentyOneValues() {
        assertThat(FsrsSchedulers.defaultParameters()).hasSize(FsrsSchedulers.PARAMETER_COUNT);
        assertThatThrownBy(() -> FsrsSchedulers.create(new double[19], 0.9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void twoConsecutiveHoldsPauseAutoQuestionsWithoutTouchingFsrs() {
        MemoryState state = reviewed(Rating.GOOD, SCHEDULER);
        Instant due = state.getDue();

        state.hold();
        assertThat(state.getConsecutiveHolds()).isEqualTo(1);
        assertThat(state.isAutoQuestionsPaused()).isFalse();

        state.hold();
        assertThat(state.getConsecutiveHolds()).isEqualTo(MemoryState.MAX_CONSECUTIVE_HOLDS);
        assertThat(state.isAutoQuestionsPaused()).isTrue();
        assertThat(state.getDue()).isEqualTo(due);
        assertThat(state.getLastReview()).isEqualTo(NOW);
    }

    @Test
    void aRatingBreaksTheHoldStreak() {
        MemoryState state = reviewed(Rating.GOOD, SCHEDULER);
        state.hold();

        state.review(SCHEDULER, 1, Rating.GOOD, NOW.plus(Duration.ofDays(3)));
        state.hold();

        assertThat(state.getConsecutiveHolds()).isEqualTo(1);
        assertThat(state.isAutoQuestionsPaused()).isFalse();
    }

    @Test
    void replayGivesTheSameStateAsReviewingOneByOne() {
        List<MemoryState.Replay> reviews = List.of(
                new MemoryState.Replay(Rating.GOOD, NOW.plus(Duration.ofMinutes(15))),
                new MemoryState.Replay(Rating.GOOD, NOW.plus(Duration.ofDays(3))),
                new MemoryState.Replay(Rating.AGAIN, NOW.plus(Duration.ofDays(12))),
                new MemoryState.Replay(Rating.HARD, NOW.plus(Duration.ofDays(13))));
        MemoryState incremental = new MemoryState(1L, 10L, 0.9, NOW);
        incremental.seed(SCHEDULER, 1, Rating.AGAIN, NOW);
        reviews.forEach(review -> incremental.review(SCHEDULER, 1, review.rating(), review.reviewedAt()));
        MemoryState replayed = new MemoryState(1L, 10L, 0.9, NOW);
        replayed.seed(SCHEDULER, 1, Rating.AGAIN, NOW);
        replayed.review(SCHEDULER, 1, Rating.EASY, NOW.plus(Duration.ofDays(1)));

        replayed.replay(SCHEDULER, 1, reviews);

        assertSameFsrsState(replayed, incremental);
    }

    @Test
    void replayWithNewParametersChangesTheStateAndRecordsTheVersion() {
        double[] custom = FsrsSchedulers.defaultParameters();
        custom[2] = 1.0;
        MemoryState state = reviewed(Rating.GOOD, SCHEDULER);
        double before = state.getStability();

        state.replay(FsrsSchedulers.create(custom, 0.9), 2, List.of(new MemoryState.Replay(Rating.GOOD, NOW)));

        assertThat(state.getStability()).isEqualTo(1.0);
        assertThat(state.getStability()).isNotEqualTo(before);
        assertThat(state.getParametersVersion()).isEqualTo(2);
    }

    @Test
    void replayWithoutAnyRatingLeavesAnUnreviewedItemAlone() {
        MemoryState state = new MemoryState(1L, 10L, 0.9, NOW);

        state.replay(SCHEDULER, 2, List.of());

        assertThat(state.getLastReview()).isNull();
        assertThat(state.getParametersVersion()).isZero();
    }

    static void assertSameFsrsState(MemoryState actual, MemoryState expected) {
        assertThat(actual.getState()).isEqualTo(expected.getState());
        assertThat(actual.getStability()).isEqualTo(expected.getStability());
        assertThat(actual.getDifficulty()).isEqualTo(expected.getDifficulty());
        assertThat(actual.getDue()).isEqualTo(expected.getDue());
        assertThat(actual.getLastReview()).isEqualTo(expected.getLastReview());
        assertThat(actual.getParametersVersion()).isEqualTo(expected.getParametersVersion());
    }
}
