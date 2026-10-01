package com.khack.review.practice.domain;

import com.khack.review.memory.domain.AnswerVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 답변 판정 1개 (스펙 §6.4.5 Jev 출력, 기록). 시도마다 하나다. Jev의 원래 결과(확률·선택·신뢰도)는 그대로 남기고, 등급 변환
 * ({@code RatingPolicy})이 신뢰도 기준을 적용한다. 근거가 `model_transcribed`면 더 높은 기준을 쓰도록 {@code evidenceFidelity}를
 * 함께 남긴다. 이유(omission·contradiction 등)는 확률이 {@link ReasonBand}에서 애매하면 확정하지 않고 {@code ambiguousReasons}에 적는다.
 */
@Entity
@Table(name = "answer_judgment")
public class AnswerJudgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JudgmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JudgedBy judgedBy;

    @Enumerated(EnumType.STRING)
    private AnswerVerdict verdict;

    private Double verdictConfidence;

    private Double omissionProbability;

    private Double contradictionProbability;

    /** `misread` 질문(choice)에서 고른 선택: misread / answered_as_asked / question_unclear. Jev의 원래 선택이다. */
    private String misreadChoice;

    /** `misread` 질문의 선택 확률 중 misread의 확률. 참고용이며 신뢰도로 쓰지 않는다. */
    private Double misreadProbability;

    /** `misread` 질문의 신뢰도(`verdict`와 별도). 등급 변환의 `misread` 신뢰도로 쓴다. */
    private Double misreadConfidence;

    @Column(nullable = false)
    private boolean omission;

    @Column(nullable = false)
    private boolean contradiction;

    @Column(nullable = false)
    private boolean misread;

    /** 대표 이유. `not_met`이 아니면 null. */
    @Enumerated(EnumType.STRING)
    private AnswerFailure primaryFailure;

    /** 헷갈린 지점 항목에서 `contradiction`이 대화 속 `userBelief`와 같은 내용인가. 헷갈린 지점 항목이 아니면 null. */
    private Boolean repeatsUserBelief;

    private Double repeatsUserBeliefProbability;

    /** 평가 대상과 무관한 부분에 틀린 내용이 있는가. 등급에 반영하지 않고 기록만 한다. */
    @Column(nullable = false)
    private boolean offTargetError;

    private Double offTargetErrorProbability;

    /** 확률이 애매해 확정하지 않은 이유(쉼표로 구분). 없으면 null. 원래 확률은 각 {@code ...Probability}에 있다. */
    @Column(length = 200)
    private String ambiguousReasons;

    /** 근거 대화의 원문 여부 ({@code verbatim} / {@code model_transcribed}). */
    @Column(nullable = false)
    private String evidenceFidelity;

    private String model;

    @Column(length = 2_000)
    private String note;

    @Column(nullable = false)
    private Instant judgedAt;

    protected AnswerJudgment() {
    }

    /** `misread` 질문(choice)의 답. {@code choice}는 Jev의 원래 선택, {@code confidence}는 그 질문의 신뢰도다. */
    public record Misread(String choice, double probability, double confidence) {

        public boolean misread() {
            return "misread".equals(choice);
        }
    }

    /** Jev 답. {@code repeatsUserBelief}는 헷갈린 지점 항목이 아니면 null이다. */
    public record Jev(AnswerVerdict verdict, double verdictConfidence, double omission, double contradiction, Misread misread,
            @Nullable Double repeatsUserBelief, double offTargetError, String model) {
    }

    /**
     * Jev 답을 해석한다. 이유는 `not_met`일 때만 확정하고, 확률이 {@code band}에서 애매하면 참·거짓으로 정하지 않고
     * {@code ambiguousReasons}에 적는다. `misread`는 Jev가 그 선택을 고른 경우이며 신뢰도는 따로 둔다.
     * 오개념 재발은 평가 대상의 `contradiction`이 확정된 헷갈린 지점 항목에서만 확정한다.
     */
    public static AnswerJudgment byJev(Long attemptId, Jev jev, ReasonBand band, String evidenceFidelity, Instant at) {
        AnswerJudgment judgment = base(attemptId, JudgmentStatus.JUDGED, JudgedBy.JEV, evidenceFidelity, at);
        judgment.verdict = jev.verdict();
        judgment.verdictConfidence = jev.verdictConfidence();
        judgment.omissionProbability = jev.omission();
        judgment.contradictionProbability = jev.contradiction();
        judgment.misreadChoice = jev.misread().choice();
        judgment.misreadProbability = jev.misread().probability();
        judgment.misreadConfidence = jev.misread().confidence();
        judgment.repeatsUserBeliefProbability = jev.repeatsUserBelief();
        judgment.offTargetErrorProbability = jev.offTargetError();
        judgment.model = jev.model();

        List<String> ambiguous = new ArrayList<>();
        boolean notMet = jev.verdict() == AnswerVerdict.NOT_MET;
        Boolean omission = band.decide(jev.omission());
        Boolean contradiction = band.decide(jev.contradiction());
        if (notMet && omission == null) {
            ambiguous.add("omission");
        }
        if (notMet && contradiction == null) {
            ambiguous.add("contradiction");
        }
        judgment.omission = notMet && Boolean.TRUE.equals(omission);
        judgment.contradiction = notMet && Boolean.TRUE.equals(contradiction);
        judgment.misread = notMet && jev.misread().misread();
        judgment.primaryFailure = judgment.contradiction ? AnswerFailure.CONTRADICTION
                : judgment.omission ? AnswerFailure.OMISSION
                : judgment.misread ? AnswerFailure.MISREAD
                : null;

        if (jev.repeatsUserBelief() != null) {
            if (!judgment.contradiction) {
                judgment.repeatsUserBelief = false;
            } else {
                judgment.repeatsUserBelief = band.decide(jev.repeatsUserBelief());
                if (judgment.repeatsUserBelief == null) {
                    ambiguous.add("repeats_user_belief");
                }
            }
        }
        Boolean offTarget = band.decide(jev.offTargetError());
        if (offTarget == null) {
            ambiguous.add("off_target_error");
        }
        judgment.offTargetError = Boolean.TRUE.equals(offTarget);
        judgment.ambiguousReasons = ambiguous.isEmpty() ? null : String.join(",", ambiguous);
        return judgment;
    }

    /** 객관식 코드 채점. 신뢰도는 1이다. 정답 번호가 없는 문제면({@code correct}가 null) 판정 불가다. */
    public static AnswerJudgment byCode(Long attemptId, @Nullable Boolean correct, String evidenceFidelity, Instant at) {
        AnswerJudgment judgment = base(attemptId, JudgmentStatus.JUDGED, JudgedBy.CODE, evidenceFidelity, at);
        judgment.verdict = correct == null ? AnswerVerdict.UNABLE_TO_JUDGE : correct ? AnswerVerdict.MET : AnswerVerdict.NOT_MET;
        judgment.verdictConfidence = 1.0;
        return judgment;
    }

    /** 판정하지 못했다(Jev 호출·해석 실패). 기억 상태를 바꾸지 않는다. */
    public static AnswerJudgment failed(Long attemptId, JudgedBy judgedBy, String note, String evidenceFidelity, Instant at) {
        AnswerJudgment judgment = base(attemptId, JudgmentStatus.FAILED, judgedBy, evidenceFidelity, at);
        judgment.note = note.length() <= 2_000 ? note : note.substring(0, 2_000);
        return judgment;
    }

    private static AnswerJudgment base(Long attemptId, JudgmentStatus status, JudgedBy judgedBy, String evidenceFidelity,
            Instant at) {
        AnswerJudgment judgment = new AnswerJudgment();
        judgment.attemptId = attemptId;
        judgment.status = status;
        judgment.judgedBy = judgedBy;
        judgment.evidenceFidelity = evidenceFidelity;
        judgment.judgedAt = at;
        return judgment;
    }

    /** 정답 기준의 근거 대화가 모델이 옮겨 적은 것인가. 등급 변환이 더 높은 신뢰도 기준을 쓴다. */
    public boolean isEvidenceTranscribed() {
        return "model_transcribed".equals(evidenceFidelity);
    }

    /** 오개념 재발: 대화에서 믿었던 틀린 내용을 다시 주장했다. */
    public boolean isMisconceptionRecurred() {
        return Boolean.TRUE.equals(repeatsUserBelief);
    }

    public Long getId() {
        return id;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public JudgmentStatus getStatus() {
        return status;
    }

    public JudgedBy getJudgedBy() {
        return judgedBy;
    }

    public AnswerVerdict getVerdict() {
        return verdict;
    }

    public Double getVerdictConfidence() {
        return verdictConfidence;
    }

    public Double getOmissionProbability() {
        return omissionProbability;
    }

    public Double getContradictionProbability() {
        return contradictionProbability;
    }

    public String getMisreadChoice() {
        return misreadChoice;
    }

    public Double getMisreadProbability() {
        return misreadProbability;
    }

    /** 등급 변환의 `misread` 신뢰도. 객관식 코드 채점처럼 `misread`를 묻지 않았으면 null. */
    public Double getMisreadConfidence() {
        return misreadConfidence;
    }

    /** 확률이 애매해 확정하지 않은 이유. */
    public List<String> getAmbiguousReasons() {
        return ambiguousReasons == null ? List.of() : List.of(ambiguousReasons.split(","));
    }

    public boolean isOmission() {
        return omission;
    }

    public boolean isContradiction() {
        return contradiction;
    }

    public boolean isMisread() {
        return misread;
    }

    public AnswerFailure getPrimaryFailure() {
        return primaryFailure;
    }

    public Boolean getRepeatsUserBelief() {
        return repeatsUserBelief;
    }

    public Double getRepeatsUserBeliefProbability() {
        return repeatsUserBeliefProbability;
    }

    public boolean isOffTargetError() {
        return offTargetError;
    }

    public Double getOffTargetErrorProbability() {
        return offTargetErrorProbability;
    }

    public String getEvidenceFidelity() {
        return evidenceFidelity;
    }

    public String getModel() {
        return model;
    }

    public String getNote() {
        return note;
    }

    public Instant getJudgedAt() {
        return judgedAt;
    }
}
