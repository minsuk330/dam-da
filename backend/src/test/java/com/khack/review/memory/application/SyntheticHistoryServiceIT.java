package com.khack.review.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.memory.application.SyntheticHistoryService.SyntheticReview;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import io.github.openspacedrepetition.Rating;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SyntheticHistoryServiceIT {

    static final AtomicLong USERS = new AtomicLong(52_000);

    static final Instant AT = Instant.parse("2026-06-01T09:00:00Z");

    @Autowired
    SyntheticHistoryService history;

    @Autowired
    ReviewLogExport export;

    @Autowired
    ReviewLogRepository logs;

    @Autowired
    MemoryStateRepository states;

    @Autowired
    FsrsParametersService parameters;

    @Autowired
    ParameterActivationService activation;

    @Test
    void replacesTheUsersHistoryAndBuildsStatesWithDefaultParameters() {
        long user = USERS.incrementAndGet();
        history.replace(user, List.of(new SyntheticReview(5, Rating.EASY, AT, 1_000)));
        int version = parameters.registerOptimized(parameters.activeParameters(user).weights(), "{}").version();
        activation.activate(user, version);

        SyntheticHistoryService.Imported imported = history.replace(user, List.of(
                new SyntheticReview(1, Rating.AGAIN, AT, 7_000),
                new SyntheticReview(1, Rating.GOOD, AT.plus(Duration.ofMinutes(15)), 4_000),
                new SyntheticReview(1, Rating.GOOD, AT.plus(Duration.ofDays(2)), 4_000),
                new SyntheticReview(2, Rating.GOOD, AT.plus(Duration.ofDays(1)), 3_000)));

        assertThat(imported).isEqualTo(new SyntheticHistoryService.Imported(2, 4));
        assertThat(parameters.activeParameters(user).version()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
        assertThat(export.pyFsrs(user)).extracting(ReviewLogExport.PyFsrsReviewLog::rating).containsExactly(1, 3, 3, 3);
        assertThat(states.findByUserId(user)).hasSize(2).allSatisfy(state -> {
            assertThat(state.getMemoryItemId()).isNegative();
            assertThat(state.getLastReview()).isNotNull();
            assertThat(state.getParametersVersion()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
        });
        List<ReviewLog> first = logs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(SyntheticHistoryService.itemId(user, 1));
        assertThat(first).extracting(ReviewLog::getElapsedDays).first().isNull();
        assertThat(first).allSatisfy(log -> assertThat(log.getPolicyVersion()).isEqualTo(ReviewLog.SYNTHETIC_POLICY_VERSION));
    }

    @Test
    void activatingAfterImportReplaysTheSyntheticHistory() {
        long user = USERS.incrementAndGet();
        history.replace(user, List.of(
                new SyntheticReview(1, Rating.GOOD, AT, 1_000),
                new SyntheticReview(1, Rating.GOOD, AT.plus(Duration.ofDays(3)), 1_000)));

        ParameterActivationService.Activated activated = activation.activate(user, FsrsParametersService.DEFAULT_VERSION);

        assertThat(activated.recomputed()).isEqualTo(1);
        assertThat(activated.skipped()).isZero();
    }

    @Test
    void rejectsCardIdsOutsideTheRange() {
        assertThatThrownBy(() -> history.replace(USERS.incrementAndGet(), List.of(new SyntheticReview(0, Rating.GOOD, AT, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
