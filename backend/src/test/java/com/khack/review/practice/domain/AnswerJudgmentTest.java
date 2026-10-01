package com.khack.review.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.memory.domain.AnswerVerdict;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnswerJudgmentTest {

    static final Instant AT = Instant.parse("2026-10-01T09:00:00Z");
    static final ReasonBand BAND = new ReasonBand(0.7, 0.3);

    /** {@code misread}가 0.5 이상이면 Jev가 `misread`를 그 신뢰도로 고른 것이다. */
    static AnswerJudgment.Misread misreadAnswer(double misread) {
        return misread >= 0.5 ? new AnswerJudgment.Misread("misread", misread, misread)
                : new AnswerJudgment.Misread("answered_as_asked", misread, 1 - misread);
    }

    static AnswerJudgment byJev(AnswerVerdict verdict, double omission, double contradiction, double misread, Double repeats,
            double offTarget) {
        return byJev(verdict, omission, contradiction, misreadAnswer(misread), repeats, offTarget, "verbatim");
    }

    static AnswerJudgment byJev(AnswerVerdict verdict, double omission, double contradiction, AnswerJudgment.Misread misread,
            Double repeats, double offTarget, String fidelity) {
        return AnswerJudgment.byJev(1L, new AnswerJudgment.Jev(verdict, 0.8, omission, contradiction, misread, repeats, offTarget,
                "fake"), BAND, fidelity, AT);
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
        assertThat(judgment.getAmbiguousReasons()).as("met의 이유는 읽지 않아 애매하다고 적지도 않음").isEmpty();
        assertThat(judgment.getMisreadProbability()).as("확률은 그대로 남김").isEqualTo(0.9);
    }

    @Test
    void contradictionOutranksOmission() {
        AnswerJudgment judgment = byJev(AnswerVerdict.NOT_MET, 0.8, 0.75, 0.1, null, 0.1);

        assertThat(judgment.isOmission()).isTrue();
        assertThat(judgment.isContradiction()).isTrue();
        assertThat(judgment.getPrimaryFailure()).isEqualTo(AnswerFailure.CONTRADICTION);
        assertThat(judgment.getRepeatsUserBelief()).as("헷갈린 지점 항목이 아님").isNull();
    }

    @Test
    void omissionAloneAndMisreadAlone() {
        assertThat(byJev(AnswerVerdict.NOT_MET, 0.8, 0.2, 0.1, null, 0.1).getPrimaryFailure()).isEqualTo(AnswerFailure.OMISSION);
        AnswerJudgment misread = byJev(AnswerVerdict.NOT_MET, 0.2, 0.2, 0.85, null, 0.1);
        assertThat(misread.isMisread()).isTrue();
        assertThat(misread.getMisreadChoice()).isEqualTo("misread");
        assertThat(misread.getMisreadConfidence()).as("misread의 신뢰도는 verdict와 따로").isEqualTo(0.85);
        assertThat(misread.getPrimaryFailure()).isEqualTo(AnswerFailure.MISREAD);
    }

    @Test
    void lowConfidenceMisreadIsStillRecordedAsTheJevChoice() {
        AnswerJudgment judgment = byJev(AnswerVerdict.NOT_MET, 0.2, 0.2, 0.6, null, 0.1);

        assertThat(judgment.isMisread()).as("신뢰도가 부족해도 Jev가 고른 선택은 그대로 기록하고, 보류 여부는 등급 변환이 정한다").isTrue();
        assertThat(judgment.getMisreadConfidence()).isEqualTo(0.6);
    }

    @Test
    void anAmbiguousQuestionIsNotMisread() {
        AnswerJudgment judgment = byJev(AnswerVerdict.NOT_MET, 0.9, 0.1, new AnswerJudgment.Misread("question_unclear", 0.1, 0.9), null,
                0.1, "verbatim");

        assertThat(judgment.isMisread()).isFalse();
        assertThat(judgment.getMisreadChoice()).isEqualTo("question_unclear");
        assertThat(judgment.getPrimaryFailure()).isEqualTo(AnswerFailure.OMISSION);
    }

    @Test
    void repeatedUserBeliefIsAMisconceptionRecurrenceOnlyWithContradiction() {
        AnswerJudgment recurred = byJev(AnswerVerdict.NOT_MET, 0.1, 0.9, 0.1, 0.8, 0.1);
        assertThat(recurred.getRepeatsUserBelief()).isTrue();
        assertThat(recurred.isMisconceptionRecurred()).isTrue();

        AnswerJudgment omissionOnly = byJev(AnswerVerdict.NOT_MET, 0.9, 0.1, 0.1, 0.8, 0.1);
        assertThat(omissionOnly.getRepeatsUserBelief()).isFalse();
        assertThat(omissionOnly.isMisconceptionRecurred()).isFalse();
        assertThat(omissionOnly.getRepeatsUserBeliefProbability()).as("원래 확률은 남김").isEqualTo(0.8);
    }

    @Test
    void ambiguousReasonProbabilitiesAreNotForcedToTrueOrFalse() {
        AnswerJudgment judgment = byJev(AnswerVerdict.NOT_MET, 0.55, 0.6, 0.1, 0.5, 0.45);

        assertThat(judgment.isOmission()).isFalse();
        assertThat(judgment.isContradiction()).isFalse();
        assertThat(judgment.getPrimaryFailure()).as("확정된 이유가 없으면 대표 이유도 없다").isNull();
        assertThat(judgment.getRepeatsUserBelief()).as("contradiction이 확정되지 않았으므로 오개념 재발이 아님").isFalse();
        assertThat(judgment.isOffTargetError()).isFalse();
        assertThat(judgment.getAmbiguousReasons()).containsExactly("omission", "contradiction", "off_target_error");
        assertThat(judgment.getOmissionProbability()).isEqualTo(0.55);
        assertThat(judgment.getContradictionProbability()).isEqualTo(0.6);
        assertThat(judgment.getVerdict()).as("verdict는 Jev의 선택 그대로").isEqualTo(AnswerVerdict.NOT_MET);
    }

    @Test
    void anAmbiguousBeliefRepeatIsLeftUndecidedNotFalse() {
        AnswerJudgment judgment = byJev(AnswerVerdict.NOT_MET, 0.1, 0.95, 0.1, 0.5, 0.1);

        assertThat(judgment.isContradiction()).isTrue();
        assertThat(judgment.getRepeatsUserBelief()).isNull();
        assertThat(judgment.isMisconceptionRecurred()).isFalse();
        assertThat(judgment.getAmbiguousReasons()).containsExactly("repeats_user_belief");
    }

    @Test
    void probabilitiesExactlyOnTheBandEdgesAreDecided() {
        AnswerJudgment judgment = byJev(AnswerVerdict.NOT_MET, 0.7, 0.3, 0.1, null, 0.7);

        assertThat(judgment.isOmission()).isTrue();
        assertThat(judgment.isContradiction()).isFalse();
        assertThat(judgment.isOffTargetError()).isTrue();
        assertThat(judgment.getAmbiguousReasons()).isEmpty();
    }

    @Test
    void offTargetErrorIsRecordedWhateverTheVerdict() {
        AnswerJudgment judgment = byJev(AnswerVerdict.MET, 0.1, 0.1, 0.1, null, 0.8);

        assertThat(judgment.isOffTargetError()).isTrue();
        assertThat(judgment.getVerdict()).isEqualTo(AnswerVerdict.MET);
    }

    @Test
    void remembersWhetherTheEvidenceWasTranscribedByTheModel() {
        AnswerJudgment transcribed = byJev(AnswerVerdict.MET, 0.1, 0.1, misreadAnswer(0.1), null, 0.1, "model_transcribed");
        AnswerJudgment verbatim = byJev(AnswerVerdict.MET, 0.1, 0.1, misreadAnswer(0.1), null, 0.1, "verbatim");

        assertThat(transcribed.isEvidenceTranscribed()).isTrue();
        assertThat(transcribed.getEvidenceFidelity()).isEqualTo("model_transcribed");
        assertThat(verbatim.isEvidenceTranscribed()).isFalse();
    }

    @Test
    void reasonBandMustLeaveAGapBetweenNoAndYes() {
        assertThatThrownBy(() -> new ReasonBand(0.5, 0.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReasonBand(0.3, 0.7)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ReasonBand(0.6, 0.4).decide(0.5)).isNull();
    }

    @Test
    void codeScoringIsFullyConfidentAndFailureKeepsTheReason() {
        AnswerJudgment correct = AnswerJudgment.byCode(1L, true, "model_transcribed", AT);
        assertThat(correct.getJudgedBy()).isEqualTo(JudgedBy.CODE);
        assertThat(correct.getVerdict()).isEqualTo(AnswerVerdict.MET);
        assertThat(correct.getVerdictConfidence()).isEqualTo(1.0);
        assertThat(correct.getEvidenceFidelity()).isEqualTo("model_transcribed");
        assertThat(correct.getMisreadConfidence()).as("객관식은 misread를 묻지 않는다").isNull();
        assertThat(AnswerJudgment.byCode(1L, false, "verbatim", AT).getVerdict()).isEqualTo(AnswerVerdict.NOT_MET);

        AnswerJudgment failed = AnswerJudgment.failed(1L, JudgedBy.JEV, "Jev 호출 실패: 529", "verbatim", AT);
        assertThat(failed.getStatus()).isEqualTo(JudgmentStatus.FAILED);
        assertThat(failed.getVerdict()).isNull();
        assertThat(failed.getNote()).contains("529");
    }
}
