package com.khack.review.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.practice.domain.AttemptRules.Classification;
import com.khack.review.practice.domain.AttemptRules.Exposure;
import com.khack.review.practice.domain.AttemptRules.History;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttemptRulesTest {

    static final Instant T0 = Instant.parse("2026-10-01T09:00:00Z");

    static Instant at(int seconds) {
        return T0.plusSeconds(seconds);
    }

    static History presented(int seconds, List<Exposure> aids, List<Instant> attempts) {
        return new History(at(seconds), aids, attempts);
    }

    @Test
    void firstAnswerWithoutAidIsTheFirstUnaidedAttempt() {
        Classification c = AttemptRules.classify(presented(0, List.of(), List.of()), null, at(20));

        assertThat(c.kind()).isEqualTo(AttemptKind.FIRST_UNAIDED);
        assertThat(c.kind().isEvaluated()).isTrue();
        assertThat(c.interpretationHelp()).isFalse();
        assertThat(c.priorAidExposed()).isFalse();
        assertThat(c.sincePrior()).isNull();
        assertThat(c.timedFrom()).isEqualTo(at(0));
    }

    @Test
    void interpretationHelpIsNotAContentHint() {
        Classification c = AttemptRules.classify(
                presented(0, List.of(new Exposure(AidType.INTERPRETATION, at(5))), List.of()), null, at(20));

        assertThat(c.kind()).isEqualTo(AttemptKind.FIRST_UNAIDED);
        assertThat(c.interpretationHelp()).isTrue();
        assertThat(c.priorAidExposed()).isFalse();
    }

    @Test
    void answerAfterAHintIsNotEvaluatedAndIsTimedFromTheHint() {
        Classification c = AttemptRules.classify(
                presented(0, List.of(new Exposure(AidType.HINT, at(30))), List.of(at(20))), null, at(45));

        assertThat(c.kind()).isEqualTo(AttemptKind.AFTER_AID);
        assertThat(c.kind().isEvaluated()).isFalse();
        assertThat(c.priorAidExposed()).isTrue();
        assertThat(c.sincePrior()).isEqualTo(Duration.ofSeconds(15));
        assertThat(c.timedFrom()).isEqualTo(at(30));
    }

    @Test
    void hintBeforeTheFirstAnswerMakesItAnAidedAttempt() {
        Classification c = AttemptRules.classify(
                presented(0, List.of(new Exposure(AidType.EXPLANATION, at(10))), List.of()), null, at(40));

        assertThat(c.kind()).isEqualTo(AttemptKind.AFTER_AID);
    }

    @Test
    void secondAnswerNeedsANewContentAidAfterThePreviousAnswer() {
        assertThatThrownBy(() -> AttemptRules.classify(presented(0, List.of(), List.of(at(20))), null, at(30)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AttemptRules.classify(
                presented(0, List.of(new Exposure(AidType.HINT, at(10)), new Exposure(AidType.INTERPRETATION, at(25))),
                        List.of(at(20))), null, at(30)))
                .as("해석 도움은 재도전 조건이 아님").isInstanceOf(IllegalStateException.class);
    }

    @Test
    void delayedRecheckRecordsTheOriginalAidAndElapsedTime() {
        History original = presented(0, List.of(new Exposure(AidType.HINT, at(30)), new Exposure(AidType.EXPLANATION, at(60))),
                List.of(at(20), at(50), at(70)));

        Classification c = AttemptRules.classify(presented(300, List.of(), List.of()), original, at(320));

        assertThat(c.kind()).isEqualTo(AttemptKind.DELAYED_RECHECK);
        assertThat(c.kind().isEvaluated()).isTrue();
        assertThat(c.priorAidExposed()).isTrue();
        assertThat(c.sincePrior()).as("마지막 내용 도움(설명)부터").isEqualTo(Duration.ofSeconds(260));
        assertThat(c.timedFrom()).isEqualTo(at(300));
    }

    @Test
    void delayedRecheckWithoutOriginalAidMeasuresFromTheLastOriginalAnswer() {
        History original = presented(0, List.of(new Exposure(AidType.INTERPRETATION, at(5))), List.of(at(20)));

        Classification c = AttemptRules.classify(presented(300, List.of(), List.of()), original, at(320));

        assertThat(c.kind()).isEqualTo(AttemptKind.DELAYED_RECHECK);
        assertThat(c.priorAidExposed()).isFalse();
        assertThat(c.sincePrior()).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void hintDuringARecheckMakesItAnAidedAttempt() {
        Classification c = AttemptRules.classify(presented(300, List.of(new Exposure(AidType.HINT, at(310))), List.of()),
                presented(0, List.of(), List.of(at(20))), at(320));

        assertThat(c.kind()).isEqualTo(AttemptKind.AFTER_AID);
    }
}
