package com.khack.review.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.ReviewUnitVerdict;
import com.khack.review.collection.domain.Fidelity;
import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnitReviewJudgeTest {

    static final UnitReviewPolicy POLICY = new UnitReviewPolicy(0.5, 0.6, 0.3, 0.7, 0.5, 3, Duration.ofSeconds(1));

    static final UnitReviewState STATE = new UnitReviewState("InnoDB", "MVCC 읽기",
            List.of(new UnitReviewState.Item("fact", "일반 SELECT는 스냅샷을 읽어 락을 걸지 않는다", List.of(1))),
            List.of(new UnitReviewState.EvidenceTurn(1, "SELECT도 락 걸어?", "understanding_check", "corrected", "일반 SELECT는 락 없음")));

    final FakeJevPort jev = new FakeJevPort();
    final List<Duration> sleeps = new ArrayList<>();
    final UnitReviewJudge judge = new UnitReviewJudge(jev, POLICY, sleeps::add);

    static JevResult result(double worth, double fit, double fitConfidence) {
        return new JevResult("fake", Map.of(
                UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(worth),
                UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(fit, Map.of(), Map.of(), fitConfidence)));
    }

    @Test
    void confidentWorthAndFitApprove() {
        jev.willReturn(result(0.9, 2.0, 0.8));

        UnitReviewOutcome outcome = judge.judge(STATE, Fidelity.model_transcribed);

        assertThat(outcome.verdict()).isEqualTo(ReviewUnitVerdict.APPROVED);
        assertThat(jev.calls()).singleElement().satisfies(call -> {
            assertThat(call.state()).isEqualTo(STATE);
            assertThat(call.questions()).containsOnlyKeys(UnitReviewQuestions.WORTH_REVIEWING, UnitReviewQuestions.EVIDENCE_FIT);
        });
    }

    @Test
    void ambiguousWorthIsHeld() {
        jev.willReturn(result(0.55, 2.0, 0.9));

        UnitReviewOutcome outcome = judge.judge(STATE, Fidelity.verbatim);

        assertThat(outcome.verdict()).isEqualTo(ReviewUnitVerdict.HELD);
        assertThat(outcome.reason()).contains("복습 가치");
    }

    @Test
    void lowFitConfidenceIsHeldEvenWhenNegative() {
        jev.willReturn(result(0.1, 0.0, 0.4));

        assertThat(judge.judge(STATE, Fidelity.verbatim).verdict()).isEqualTo(ReviewUnitVerdict.HELD);
    }

    @Test
    void transcribedInputNeedsHigherConfidence() {
        jev.willReturn(result(0.9, 2.0, 0.55));

        assertThat(judge.judge(STATE, Fidelity.verbatim).verdict()).isEqualTo(ReviewUnitVerdict.APPROVED);
        assertThat(judge.judge(STATE, Fidelity.model_transcribed).verdict()).isEqualTo(ReviewUnitVerdict.HELD);
    }

    @Test
    void confidentlyNotWorthReviewingIsRejected() {
        jev.willReturn(result(0.1, 2.0, 0.9));

        UnitReviewOutcome outcome = judge.judge(STATE, Fidelity.verbatim);

        assertThat(outcome.verdict()).isEqualTo(ReviewUnitVerdict.REJECTED);
        assertThat(outcome.reason()).startsWith("복습 가치 없음");
    }

    @Test
    void confidentlyPoorEvidenceFitIsRejected() {
        jev.willReturn(result(0.9, 0.5, 0.9));

        UnitReviewOutcome outcome = judge.judge(STATE, Fidelity.verbatim);

        assertThat(outcome.verdict()).isEqualTo(ReviewUnitVerdict.REJECTED);
        assertThat(outcome.reason()).startsWith("근거 연결 부족");
    }

    @Test
    void retryableFailureIsRetriedWithGrowingDelay() {
        jev.willRespond(new JevCallException(429, "rate limited", null), new JevCallException(529, "overloaded", null),
                result(0.9, 2.0, 0.8));

        UnitReviewOutcome outcome = judge.judge(STATE, Fidelity.verbatim);

        assertThat(outcome.verdict()).isEqualTo(ReviewUnitVerdict.APPROVED);
        assertThat(jev.calls()).hasSize(3);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void exhaustedRetriesHandOverToUser() {
        jev.willFail(new JevCallException(529, "overloaded", null));

        UnitReviewOutcome outcome = judge.judge(STATE, Fidelity.verbatim);

        assertThat(outcome.verdict()).isEqualTo(ReviewUnitVerdict.UNAVAILABLE);
        assertThat(jev.calls()).hasSize(POLICY.maxAttempts());
    }

    @Test
    void nonRetryableFailureIsNotRetried() {
        jev.willFail(new JevCallException(401, "unauthorized", null));

        UnitReviewOutcome outcome = judge.judge(STATE, Fidelity.verbatim);

        assertThat(outcome.verdict()).isEqualTo(ReviewUnitVerdict.UNAVAILABLE);
        assertThat(jev.calls()).hasSize(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void missingAnswerHandsOverToUser() {
        jev.willReturn(new JevResult("fake", Map.of(UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(0.9))));

        assertThat(judge.judge(STATE, Fidelity.verbatim).verdict()).isEqualTo(ReviewUnitVerdict.UNAVAILABLE);
    }
}
