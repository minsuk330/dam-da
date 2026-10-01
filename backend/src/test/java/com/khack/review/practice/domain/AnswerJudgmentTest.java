package com.khack.review.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.memory.domain.AnswerVerdict;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnswerJudgmentTest {

    static final Instant AT = Instant.parse("2026-10-01T09:00:00Z");

    static AnswerJudgment byJev(AnswerVerdict verdict, double omission, double contradiction, double misread, Double repeats,
            double offTarget) {
        return AnswerJudgment.byJev(1L, new AnswerJudgment.Jev(verdict, 0.8, omission, contradiction, misread, repeats, offTarget,
                "fake"), 0.5, "verbatim", AT);
    }

    @Test
    void metHasNoFailuresEvenIfNoulProbabilitiesAreHigh() {
        AnswerJudgment judgment = byJev(AnswerVerdict.MET, 0.9, 0.9, 0.9, 0.9, 0.1);

        assertThat(judgment.getStatus()).isEqualTo(JudgmentStatus.JUDGED);
        assertThat(judgment.isOmission()).isFalse();
        assertThat(judgment.isContradiction()).isFalse();
        assertThat(judgment.isMisread()).isFalse();
        assertThat(judgment.getPrimaryFailure()).isNull();
        assertThat(judgment.getRepeatsUserBelief()).isFalse();
        assertThat(judgment.isMisconceptionRecurred()).isFalse();
        assertThat(judgment.getMisreadProbability()).as("확률은 그대로 남김").isEqualTo(0.9);
    }

    @Test
    void contradictionOutranksOmission() {
        AnswerJudgment judgment = byJev(AnswerVerdict.NOT_MET, 0.8, 0.7, 0.1, null, 0.1);

        assertThat(judgment.isOmission()).isTrue();
        assertThat(judgment.isContradiction()).isTrue();
        assertThat(judgment.getPrimaryFailure()).isEqualTo(AnswerFailure.CONTRADICTION);
        assertThat(judgment.getRepeatsUserBelief()).as("헷갈린 지점 항목이 아님").isNull();
    }

    @Test
    void omissionAloneAndMisreadAlone() {
        assertThat(byJev(AnswerVerdict.NOT_MET, 0.8, 0.2, 0.1, null, 0.1).getPrimaryFailure()).isEqualTo(AnswerFailure.OMISSION);
        AnswerJudgment misread = byJev(AnswerVerdict.NOT_MET, 0.2, 0.2, 0.75, null, 0.1);
        assertThat(misread.isMisread()).isTrue();
        assertThat(misread.getPrimaryFailure()).isEqualTo(AnswerFailure.MISREAD);
    }

    @Test
    void repeatedUserBeliefIsAMisconceptionRecurrenceOnlyWithContradiction() {
        AnswerJudgment recurred = byJev(AnswerVerdict.NOT_MET, 0.1, 0.9, 0.1, 0.8, 0.1);
        assertThat(recurred.getRepeatsUserBelief()).isTrue();
        assertThat(recurred.isMisconceptionRecurred()).isTrue();

        AnswerJudgment omissionOnly = byJev(AnswerVerdict.NOT_MET, 0.9, 0.1, 0.1, 0.8, 0.1);
        assertThat(omissionOnly.getRepeatsUserBelief()).isFalse();
        assertThat(omissionOnly.isMisconceptionRecurred()).isFalse();
    }

    @Test
    void offTargetErrorIsRecordedWhateverTheVerdict() {
        AnswerJudgment judgment = byJev(AnswerVerdict.MET, 0.1, 0.1, 0.1, null, 0.6);

        assertThat(judgment.isOffTargetError()).isTrue();
        assertThat(judgment.getVerdict()).isEqualTo(AnswerVerdict.MET);
    }

    @Test
    void codeScoringIsFullyConfidentAndFailureKeepsTheReason() {
        AnswerJudgment correct = AnswerJudgment.byCode(1L, true, "model_transcribed", AT);
        assertThat(correct.getJudgedBy()).isEqualTo(JudgedBy.CODE);
        assertThat(correct.getVerdict()).isEqualTo(AnswerVerdict.MET);
        assertThat(correct.getVerdictConfidence()).isEqualTo(1.0);
        assertThat(correct.getEvidenceFidelity()).isEqualTo("model_transcribed");
        assertThat(AnswerJudgment.byCode(1L, false, "verbatim", AT).getVerdict()).isEqualTo(AnswerVerdict.NOT_MET);

        AnswerJudgment failed = AnswerJudgment.failed(1L, JudgedBy.JEV, "Jev 호출 실패: 529", "verbatim", AT);
        assertThat(failed.getStatus()).isEqualTo(JudgmentStatus.FAILED);
        assertThat(failed.getVerdict()).isNull();
        assertThat(failed.getNote()).contains("529");
    }
}
