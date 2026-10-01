package com.khack.review.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.json.Json;
import com.khack.review.memory.application.ReviewLogExport.PyFsrsReviewLog;
import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.memory.domain.HoldReason;
import com.khack.review.memory.domain.MemoryStateUpdated;
import com.khack.review.memory.domain.RatingDecision;
import com.khack.review.memory.domain.RatingPolicy;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.ReviewContext;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import com.khack.review.memory.domain.ReviewScheduled;
import com.khack.review.memory.domain.SelfAssessment;
import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** 판정된 시도 → 등급 변환 → 복습 기록 → FSRS 갱신·보류 (스펙 §6.4.5). 판정은 가짜 값으로 넣는다. */
@SpringBootTest
@Import(ReviewRecordServiceIT.Events.class)
class ReviewRecordServiceIT {

    /** 다른 테스트와 DB를 공유하므로 겹치지 않는 ID를 쓴다. */
    static final AtomicLong IDS = new AtomicLong(800_000);

    static final Instant AT = Instant.parse("2026-10-01T09:00:00Z");

    @TestConfiguration
    static class Events {

        @Bean
        Received received() {
            return new Received();
        }
    }

    static class Received {

        final List<Object> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(MemoryStateUpdated event) {
            events.add(event);
        }

        @EventListener
        void on(ReviewScheduled event) {
            events.add(event);
        }
    }

    @Autowired
    ReviewRecordService reviews;

    @Autowired
    ReviewLogRepository logs;

    @Autowired
    MemoryStateService memory;

    @Autowired
    ReviewLogExport export;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    Received received;

    @BeforeEach
    void reset() {
        received.events.clear();
    }

    ReviewContext context(long item, Instant at) {
        return new ReviewContext(currentUser.id(), item, 70L, IDS.incrementAndGet(), at, 0.8, false, null, false,
                List.of("omission"), 5_000L);
    }

    static RatingInput input(AnswerVerdict verdict, double confidence, SelfAssessment self) {
        return new RatingInput(AttemptKind.FIRST_UNASSISTED, verdict, confidence, false, 0, false, self,
                QuestionType.SHORT_ANSWER, Duration.ofSeconds(20));
    }

    @Test
    void ratedAttemptUpdatesFsrsLogsAndPublishes() {
        long item = IDS.incrementAndGet();
        ReviewContext first = context(item, AT);

        ReviewRecordService.Recorded recorded = reviews.record(first, input(AnswerVerdict.MET, 0.9, SelfAssessment.RECALLED_EASILY));

        assertThat(recorded.decision()).isEqualTo(new RatingDecision.Rated(Rating.EASY, 6, RatingPolicy.VERSION));
        assertThat(recorded.review()).isPresent();
        assertThat(memory.lastReviewedAt(item)).contains(AT);
        ReviewLog log = logs.findByAttemptId(first.attemptId()).orElseThrow();
        assertThat(log.getRating()).isEqualTo(Rating.EASY);
        assertThat(log.getHoldReason()).isNull();
        assertThat(log.getPolicyVersion()).isEqualTo(RatingPolicy.VERSION);
        assertThat(log.getEvidenceTranscribed()).as("원문 근거로 판정").isFalse();
        assertThat(log.getAppliedMinConfidence()).as("적용한 기준을 기록한다").isEqualTo(0.8);
        assertThat(log.getParametersVersion()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
        assertThat(log.getElapsedDays()).as("첫 등급").isNull();
        assertThat(log.getPredictedRetrievability()).isEqualTo(0.8);
        assertThat(log.getFailures()).isEqualTo("omission");
        assertThat(log.getResponseTimeMs()).isEqualTo(20_000);
        assertThat(log.getFirstInputMs()).isEqualTo(5_000);
        assertThat(received.events).containsExactly(
                new MemoryStateUpdated(currentUser.id(), item, Rating.EASY, AT, recorded.review().get().state(),
                        recorded.review().get().stability(), recorded.review().get().difficulty()),
                new ReviewScheduled(currentUser.id(), item, recorded.review().get().due()));

        ReviewContext second = context(item, AT.plus(Duration.ofDays(3)).plus(Duration.ofHours(12)));
        reviews.record(second, input(AnswerVerdict.NOT_MET, 0.9, SelfAssessment.RECALLED_WITH_EFFORT));

        ReviewLog again = logs.findByAttemptId(second.attemptId()).orElseThrow();
        assertThat(again.getRating()).as("실패에 Hard 없음").isEqualTo(Rating.AGAIN);
        assertThat(again.getElapsedDays()).isCloseTo(3.5, within(1e-9));
    }

    @Test
    void heldAttemptKeepsFsrsAndCountsTheStreak() {
        long item = IDS.incrementAndGet();
        reviews.record(context(item, AT), input(AnswerVerdict.MET, 0.9, SelfAssessment.RECALLED_EASILY));
        Instant due = memory.nextReviewAt(item).orElseThrow();
        received.events.clear();

        ReviewContext held = context(item, AT.plus(Duration.ofDays(1)));
        ReviewRecordService.Recorded first = reviews.record(held, input(AnswerVerdict.MET, 0.3, SelfAssessment.RECALLED_EASILY));
        ReviewRecordService.Recorded second = reviews.record(context(item, AT.plus(Duration.ofDays(2))),
                input(AnswerVerdict.UNABLE_TO_JUDGE, 0.9, SelfAssessment.RECALLED_EASILY));

        assertThat(first.decision()).isEqualTo(new RatingDecision.Held(HoldReason.LOW_CONFIDENCE, 1, RatingPolicy.VERSION));
        assertThat(first.hold()).hasValueSatisfying(hold -> assertThat(hold.autoQuestionsPaused()).isFalse());
        assertThat(second.hold()).hasValueSatisfying(hold -> assertThat(hold.autoQuestionsPaused()).isTrue());
        assertThat(memory.nextReviewAt(item)).contains(due);
        assertThat(memory.lastReviewedAt(item)).contains(AT);
        ReviewLog log = logs.findByAttemptId(held.attemptId()).orElseThrow();
        assertThat(log.getRating()).isNull();
        assertThat(log.getHoldReason()).isEqualTo(HoldReason.LOW_CONFIDENCE);
        assertThat(log.getParametersVersion()).isNull();
        assertThat(received.events).isEmpty();
    }

    @Test
    void assistedRetryIsNotLoggedAndRepeatsAreIgnored() {
        long item = IDS.incrementAndGet();
        ReviewContext retry = context(item, AT);
        RatingInput aided = new RatingInput(AttemptKind.ASSISTED_RETRY, AnswerVerdict.MET, 0.9, false, 0, false,
                SelfAssessment.RECALLED_EASILY, QuestionType.SHORT_ANSWER, Duration.ofSeconds(20));

        assertThat(reviews.record(retry, aided).decision()).isInstanceOf(RatingDecision.NotEvaluated.class);
        assertThat(logs.findByAttemptId(retry.attemptId())).isEmpty();
        assertThat(memory.isReviewed(item)).isFalse();

        ReviewContext once = context(item, AT);
        reviews.record(once, input(AnswerVerdict.NOT_MET, 0.9, SelfAssessment.RECALLED_EASILY));
        ReviewRecordService.Recorded repeated = reviews.record(once, input(AnswerVerdict.MET, 0.9, SelfAssessment.RECALLED_EASILY));

        assertThat(repeated.decision()).isEqualTo(new RatingDecision.Rated(Rating.AGAIN, 4, RatingPolicy.VERSION));
        assertThat(repeated.review()).isEmpty();
        assertThat(logs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(item)).hasSize(1);
    }

    @Test
    void pyFsrsExportHasRatedLogsOnly() {
        long item = IDS.incrementAndGet();
        reviews.record(context(item, AT), input(AnswerVerdict.MET, 0.9, SelfAssessment.RECALLED_WITH_EFFORT));
        reviews.record(context(item, AT.plus(Duration.ofDays(1))), input(AnswerVerdict.MET, 0.1, SelfAssessment.RECALLED_EASILY));

        List<PyFsrsReviewLog> exported = export.pyFsrs(currentUser.id()).stream().filter(log -> log.cardId() == item).toList();

        assertThat(exported).containsExactly(new PyFsrsReviewLog(item, 2, "2026-10-01T09:00:00Z", 20_000));
        assertThat(Json.MAPPER.writeValueAsString(exported.getFirst())).isEqualTo(
                "{\"card_id\":%d,\"rating\":2,\"review_datetime\":\"2026-10-01T09:00:00Z\",\"review_duration\":20000}".formatted(item));
    }
}
