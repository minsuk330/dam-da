package com.khack.review.memory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.memory.application.MemoryModelService.CurvePoint;
import com.khack.review.memory.application.MemoryModelService.MemoryModel;
import com.khack.review.memory.application.MemoryModelService.ModelStatus;
import com.khack.review.memory.application.SyntheticHistoryService.SyntheticReview;
import com.khack.review.memory.domain.FsrsSchedulers;
import io.github.openspacedrepetition.Rating;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 내 기억 패턴 요약 (#71). 다른 테스트와 겹치지 않게 별도 사용자 ID를 쓴다. */
@SpringBootTest
class MemoryModelServiceIT {

    static final AtomicLong USERS = new AtomicLong(51_000);

    static final Instant AT = Instant.parse("2026-09-01T09:00:00Z");

    @Autowired
    MemoryModelService models;

    @Autowired
    SyntheticHistoryService history;

    @Autowired
    FsrsParametersService parameters;

    @Autowired
    ParameterActivationService activation;

    @Test
    void newUserSeesTheDefaultModelWithEmptyProgress() {
        MemoryModel model = models.model(USERS.incrementAndGet());

        assertThat(model.status()).isEqualTo(ModelStatus.DEFAULT);
        assertThat(model.parametersVersion()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
        assertThat(model.progress().gradedReviews()).isZero();
        assertThat(model.progress().requiredReviews()).isEqualTo(1000);
        assertThat(model.defaultCurve()).isNull();
        assertThat(model.predictionImprovement()).isNull();
        assertThat(model.typicalStabilityDays()).isNull();
        assertThat(model.reviewedItems()).isZero();
        assertThat(model.firstRecallDays()).isEqualTo(FsrsSchedulers.defaultParameters()[2]);
        assertThat(model.curve()).hasSize(31);
        assertThat(model.curve().getFirst()).isEqualTo(new CurvePoint(0, 1.0));
        assertThat(model.curve()).isSortedAccordingTo((a, b) -> Double.compare(b.retrievability(), a.retrievability()));
    }

    @Test
    void reviewedItemsGiveProgressAndTypicalStability() {
        long user = USERS.incrementAndGet();
        history.replace(user, List.of(
                new SyntheticReview(1, Rating.GOOD, AT, 5_000),
                new SyntheticReview(1, Rating.GOOD, AT.plus(Duration.ofDays(3)), 5_000),
                new SyntheticReview(2, Rating.AGAIN, AT, 5_000)));

        MemoryModel model = models.model(user);

        assertThat(model.progress().gradedReviews()).isEqualTo(3);
        assertThat(model.reviewedItems()).isEqualTo(2);
        assertThat(model.typicalStabilityDays()).isPositive();
    }

    @Test
    void personalizedModelComparesWithTheDefaultCurveAndShowsTheValidatedImprovement() {
        long user = USERS.incrementAndGet();
        double[] faster = FsrsSchedulers.defaultParameters();
        faster[2] = 1.0;
        int version = parameters.registerOptimized(user, faster, "{\"improvement\": 0.055}").version();
        activation.activate(user, version);

        MemoryModel model = models.model(user);

        assertThat(model.status()).isEqualTo(ModelStatus.PERSONALIZED);
        assertThat(model.parametersVersion()).isEqualTo(version);
        assertThat(model.firstRecallDays()).isEqualTo(1.0);
        assertThat(model.predictionImprovement()).isEqualTo(0.055);
        assertThat(model.defaultCurve()).hasSize(31);
        assertThat(model.curve().get(7).retrievability()).isLessThan(model.defaultCurve().get(7).retrievability());
    }
}
