package com.khack.review.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.memory.domain.FsrsSchedulers;
import com.khack.review.memory.domain.MemoryState;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.ReviewContext;
import com.khack.review.memory.domain.ReviewLogRepository;
import com.khack.review.memory.domain.SelfAssessment;
import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 매개변수 버전 전환·롤백과 기억 상태 재계산 (스펙 §6.4.9). 다른 테스트와 겹치지 않게 별도 사용자 ID를 쓴다. */
@SpringBootTest
class ParameterActivationServiceIT {

    static final AtomicLong IDS = new AtomicLong(600_000);

    static final Instant AT = Instant.parse("2026-09-01T09:00:00Z");

    @Autowired
    ParameterActivationService activation;

    @Autowired
    FsrsParametersService parameters;

    @Autowired
    MemoryStateService memory;

    @Autowired
    ReviewRecordService reviews;

    @Autowired
    MemoryStateRepository states;

    @Autowired
    ReviewLogRepository logs;

    void record(long user, long item, AnswerVerdict verdict, Instant at) {
        reviews.record(new ReviewContext(user, item, 70L, IDS.incrementAndGet(), at, null, false, null, false, List.of(), 5_000L),
                new RatingInput(AttemptKind.FIRST_UNASSISTED, verdict, 0.95, false, 0, false, SelfAssessment.RECALLED_WITH_EFFORT,
                        QuestionType.SHORT_ANSWER, Duration.ofSeconds(20)));
    }

    MemoryState state(long item) {
        return states.findByMemoryItemId(item).orElseThrow();
    }

    @Test
    void activatingRecomputesFromTheRecordsAndRollingBackRestoresTheIncrementalState() {
        long user = IDS.incrementAndGet();
        long seeded = IDS.incrementAndGet();
        long practiced = IDS.incrementAndGet();
        long legacy = IDS.incrementAndGet();
        long unreviewed = IDS.incrementAndGet();
        memory.seed(user, seeded, Rating.AGAIN, AT);
        record(user, seeded, AnswerVerdict.MET, AT.plus(Duration.ofMinutes(15)));
        record(user, seeded, AnswerVerdict.MET, AT.plus(Duration.ofDays(3)));
        record(user, seeded, AnswerVerdict.NOT_MET, AT.plus(Duration.ofDays(12)));
        record(user, practiced, AnswerVerdict.MET, AT.plus(Duration.ofDays(1)));
        record(user, practiced, AnswerVerdict.MET, AT.plus(Duration.ofDays(5)));
        memory.review(user, legacy, Rating.GOOD, AT);
        record(user, legacy, AnswerVerdict.MET, AT.plus(Duration.ofDays(4)));
        memory.applyRetention(user, List.of(unreviewed), 0.9);
        MemoryState seededBefore = state(seeded);
        MemoryState practicedBefore = state(practiced);
        double legacyStability = state(legacy).getStability();

        double[] custom = FsrsSchedulers.defaultParameters();
        for (int i = 0; i < 4; i++) {
            custom[i] *= 0.3;
        }
        int version = parameters.registerOptimized(user, custom, "{}").version();
        ParameterActivationService.Activated activated = activation.activate(user, version);

        assertThat(activated.parameters().version()).isEqualTo(version);
        assertThat(activated.recomputed()).isEqualTo(2);
        assertThat(activated.skipped()).isEqualTo(1);
        assertThat(state(seeded).getParametersVersion()).isEqualTo(version);
        assertThat(state(seeded).getStability()).isNotEqualTo(seededBefore.getStability());
        assertThat(state(practiced).getStability()).isNotEqualTo(practicedBefore.getStability());
        assertThat(state(legacy).getStability()).isEqualTo(legacyStability);
        assertThat(state(unreviewed).getLastReview()).isNull();
        assertThat(parameters.activeParameters(user).version()).isEqualTo(version);

        record(user, practiced, AnswerVerdict.MET, AT.plus(Duration.ofDays(20)));
        assertThat(logs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(practiced).getLast().getParametersVersion()).isEqualTo(version);

        activation.activate(user, FsrsParametersService.DEFAULT_VERSION);
        MemoryState seededAfter = state(seeded);
        assertThat(seededAfter.getStability()).isEqualTo(seededBefore.getStability());
        assertThat(seededAfter.getDifficulty()).isEqualTo(seededBefore.getDifficulty());
        assertThat(seededAfter.getDue()).isEqualTo(seededBefore.getDue());
        assertThat(seededAfter.getLastReview()).isEqualTo(seededBefore.getLastReview());
        assertThat(seededAfter.getParametersVersion()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
    }

    @Test
    void rejectsAnUnknownVersion() {
        assertThatThrownBy(() -> activation.activate(IDS.incrementAndGet(), 9_999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void personalParametersBelongToTheirOwner() {
        long owner = IDS.incrementAndGet();
        long other = IDS.incrementAndGet();
        int version = parameters.registerOptimized(owner, FsrsSchedulers.defaultParameters(), "{}").version();

        assertThat(parameters.all(owner)).extracting(FsrsParametersService.ParameterSet::version).contains(1, version);
        assertThat(parameters.all(other)).extracting(FsrsParametersService.ParameterSet::version).contains(1).doesNotContain(version);
        assertThatThrownBy(() -> activation.activate(other, version)).isInstanceOf(IllegalArgumentException.class);
        assertThat(activation.activate(owner, version).parameters().version()).isEqualTo(version);
    }
}
