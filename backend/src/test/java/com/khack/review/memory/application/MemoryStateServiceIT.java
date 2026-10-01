package com.khack.review.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.memory.domain.FsrsParameters;
import com.khack.review.memory.domain.FsrsParametersRepository;
import com.khack.review.memory.domain.FsrsSchedulers;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.ParameterSource;
import com.khack.review.memory.domain.RatingPolicy;
import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.State;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MemoryStateServiceIT {

    /** 다른 테스트와 DB를 공유하므로 겹치지 않는 기억 항목 ID를 쓴다. */
    static final AtomicLong ITEM_IDS = new AtomicLong(900_000);

    @Autowired
    MemoryStateService memory;

    @Autowired
    MemoryStateRepository states;

    @Autowired
    FsrsParametersRepository parameters;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    Clock clock;

    @AfterEach
    void resetClock() {
        ((TimeTravelClock) clock).reset();
    }

    @Test
    void defaultParametersAreStoredAsVersionOne() {
        FsrsParameters defaults = parameters.findByVersion(FsrsParametersService.DEFAULT_VERSION).orElseThrow();

        assertThat(defaults.getSource()).isEqualTo(ParameterSource.DEFAULT);
        assertThat(defaults.weights()).containsExactly(FsrsSchedulers.defaultParameters());
    }

    @Test
    void firstRatingCreatesAndStoresTheState() {
        long item = ITEM_IDS.incrementAndGet();
        assertThat(memory.retrievability(item)).isEmpty();
        assertThat(memory.nextReviewAt(item)).isEmpty();

        MemoryStateService.ReviewResult result = memory.review(currentUser.id(), item, Rating.GOOD);

        assertThat(result.retrievabilityBefore()).isEmpty();
        assertThat(result.state()).isEqualTo(State.REVIEW);
        assertThat(result.parametersVersion()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
        assertThat(states.findByMemoryItemId(item)).hasValueSatisfying(state -> {
            assertThat(state.getDue()).isEqualTo(result.due());
            assertThat(state.getDesiredRetention()).isEqualTo(0.9);
        });
        assertThat(memory.nextReviewAt(item)).contains(result.due());
    }

    @Test
    void timeTravelLowersRetrievabilityAndTheNextRatingSeesIt() {
        long item = ITEM_IDS.incrementAndGet();
        memory.review(currentUser.id(), item, Rating.GOOD);
        double now = memory.retrievability(item).orElseThrow();

        ((TimeTravelClock) clock).travel(Duration.ofDays(7));
        double weekLater = memory.retrievability(item).orElseThrow();
        MemoryStateService.ReviewResult second = memory.review(currentUser.id(), item, Rating.AGAIN);

        assertThat(weekLater).isLessThan(now);
        assertThat(second.retrievabilityBefore()).hasValueSatisfying(r -> assertThat(r).isEqualTo(weekLater));
        assertThat(second.due()).isAfter(clock.instant());
    }

    @Autowired
    RatingPolicy ratingPolicy;

    @Test
    void holdsAreCountedWithoutAnFsrsStateChange() {
        long item = ITEM_IDS.incrementAndGet();

        MemoryStateService.HoldResult first = memory.hold(currentUser.id(), item);
        MemoryStateService.HoldResult second = memory.hold(currentUser.id(), item);

        assertThat(first.autoQuestionsPaused()).isFalse();
        assertThat(second.consecutiveHolds()).isEqualTo(2);
        assertThat(second.autoQuestionsPaused()).isTrue();
        assertThat(memory.isReviewed(item)).isFalse();
        assertThat(memory.nextReviewAt(item)).isEmpty();
    }

    @Test
    void ratingPolicyIsBoundFromConfiguration() {
        assertThat(ratingPolicy.minConfidence()).isEqualTo(0.6);
        assertThat(ratingPolicy.minConfidenceTranscribed()).isEqualTo(0.7);
        assertThat(ratingPolicy.referenceTimes()).containsEntry(
                QuestionType.SHORT_ANSWER, Duration.ofSeconds(40));
        assertThat(ratingPolicy.easyMaxTimeRatio()).isEqualTo(1.5);
    }
}
