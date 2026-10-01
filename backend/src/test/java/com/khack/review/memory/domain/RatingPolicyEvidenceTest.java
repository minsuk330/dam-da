package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 자동 반영 기준(여기서는 0.80)과, 모델이 옮겨 적은 근거(`model_transcribed`)에 더 높은 기준을 쓰는 것(스펙 §6.4.5, §7.3). */
class RatingPolicyEvidenceTest {

    static final RatingPolicy POLICY = new RatingPolicy(0.80, 0.85,
            Map.of(QuestionType.SHORT_ANSWER, Duration.ofSeconds(40)), 1.5);

    static RatingInput input(AnswerVerdict verdict, double confidence, boolean misread, double misreadConfidence, boolean transcribed) {
        return new RatingInput(AttemptKind.FIRST_UNASSISTED, verdict, confidence, misread, misreadConfidence, false,
                SelfAssessment.RECALLED_WITH_EFFORT, QuestionType.SHORT_ANSWER, Duration.ofSeconds(30), transcribed);
    }

    @Test
    void verdictConfidenceBelowTheThresholdHoldsAndTheThresholdItselfPasses() {
        assertThat(POLICY.decide(input(AnswerVerdict.MET, 0.79, false, 0, false)))
                .isEqualTo(new RatingDecision.Held(HoldReason.LOW_CONFIDENCE, 1, RatingPolicy.VERSION));
        assertThat(POLICY.decide(input(AnswerVerdict.MET, 0.80, false, 0, false)))
                .isEqualTo(new RatingDecision.Rated(Rating.HARD, 5, RatingPolicy.VERSION));
        assertThat(POLICY.decide(input(AnswerVerdict.NOT_MET, 0.79, false, 0, false)))
                .as("오답도 신뢰도가 부족하면 기억 상태를 바꾸지 않는다")
                .isEqualTo(new RatingDecision.Held(HoldReason.LOW_CONFIDENCE, 1, RatingPolicy.VERSION));
    }

    @Test
    void evidenceTranscribedNeedsAHigherConfidence() {
        assertThat(POLICY.decide(input(AnswerVerdict.MET, 0.82, false, 0, false))).isInstanceOf(RatingDecision.Rated.class);
        assertThat(POLICY.decide(input(AnswerVerdict.MET, 0.82, false, 0, true)))
                .isEqualTo(new RatingDecision.Held(HoldReason.LOW_CONFIDENCE, 1, RatingPolicy.VERSION));
        assertThat(POLICY.decide(input(AnswerVerdict.MET, 0.85, false, 0, true))).isInstanceOf(RatingDecision.Rated.class);
        assertThat(POLICY.minConfidenceFor(false)).isEqualTo(0.80);
        assertThat(POLICY.minConfidenceFor(true)).isEqualTo(0.85);
    }

    @Test
    void confidentMisreadHoldsButLowConfidenceMisreadOnNotMetIsAgain() {
        assertThat(POLICY.decide(input(AnswerVerdict.NOT_MET, 0.95, true, 0.80, false)))
                .isEqualTo(new RatingDecision.Held(HoldReason.MISREAD, 2, RatingPolicy.VERSION));
        assertThat(POLICY.decide(input(AnswerVerdict.NOT_MET, 0.95, true, 0.79, false)))
                .as("not_met에서 misread 신뢰도만 부족하면 보류가 아니라 Again")
                .isEqualTo(new RatingDecision.Rated(Rating.AGAIN, 4, RatingPolicy.VERSION));
    }

    @Test
    void misreadUsesTheSameSourceSpecificThreshold() {
        assertThat(POLICY.decide(input(AnswerVerdict.NOT_MET, 0.95, true, 0.82, true)))
                .as("옮겨 적은 근거에서는 0.82가 misread 확인 기준(0.85)에 못 미친다")
                .isEqualTo(new RatingDecision.Rated(Rating.AGAIN, 4, RatingPolicy.VERSION));
    }

    @Test
    void thePolicyWithoutASeparateTranscribedThresholdUsesOneThresholdForBoth() {
        RatingPolicy single = new RatingPolicy(0.8, Map.of(), 1.5);

        assertThat(single.minConfidenceFor(true)).isEqualTo(single.minConfidenceFor(false));
    }

    @Test
    void transcribedThresholdCannotBeLowerThanTheVerbatimOne() {
        assertThatThrownBy(() -> new RatingPolicy(0.8, 0.7, Map.of(), 1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
