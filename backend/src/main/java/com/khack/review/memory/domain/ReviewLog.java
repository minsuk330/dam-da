package com.khack.review.memory.domain;

import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 평가 대상 시도 1회의 복습 기록 (스펙 §6.4.5 기록, §6.4.9). 등급 또는 보류와 그 근거를 남겨 정책 영향을 비교하고,
 * 등급이 있는 기록은 개인 매개변수 학습(py-fsrs 옵티마이저)에 쓴다. 풀이 시도는 ID로만 참조한다.
 */
@Entity
@Table(name = "review_log")
public class ReviewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long memoryItemId;

    @Column(nullable = false)
    private Long questionId;

    /** 풀이 시도. 같은 시도를 두 번 기록하지 않는다. */
    @Column(nullable = false, unique = true)
    private Long attemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    @Column(nullable = false)
    private Instant reviewedAt;

    /** 직전 등급 이후 경과 일수. 첫 등급이면 null. */
    private Double elapsedDays;

    /** 등급. 보류면 null. */
    @Enumerated(EnumType.STRING)
    private Rating rating;

    /** 보류 이유. 등급이 있으면 null. */
    @Enumerated(EnumType.STRING)
    private HoldReason holdReason;

    /** 적용한 변환표 행(1~7). */
    @Column(nullable = false)
    private int policyRow;

    @Column(nullable = false)
    private int policyVersion;

    /** 등급 반영에 쓴 FSRS 매개변수 버전. 보류면 null. */
    private Integer parametersVersion;

    /** 답변 직전의 예측 R. 첫 등급 전이면 null. */
    private Double predictedRetrievability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptKind attemptKind;

    @Column(nullable = false)
    private boolean priorAidExposed;

    private Long elapsedSincePriorMs;

    @Column(nullable = false)
    private boolean sameDayRecheck;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerVerdict verdict;

    @Column(nullable = false)
    private double verdictConfidence;

    @Column(nullable = false)
    private boolean misread;

    @Column(nullable = false)
    private double misreadConfidence;

    /** 판정 이유(omission, contradiction, misread). 쉼표로 잇는다. 객관식이면 null. */
    private String failures;

    @Column(nullable = false)
    private boolean guessSuspected;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SelfAssessment selfAssessment;

    @Column(nullable = false)
    private long responseTimeMs;

    private Long firstInputMs;

    protected ReviewLog() {
    }

    /** 등급 또는 보류 결정을 남긴다. 평가 대상이 아닌 시도({@link RatingDecision.NotEvaluated})는 기록하지 않는다. */
    public static ReviewLog of(ReviewContext context, RatingInput input, RatingDecision decision, @Nullable Double elapsedDays,
            @Nullable Integer parametersVersion) {
        ReviewLog log = new ReviewLog();
        switch (decision) {
            case RatingDecision.Rated rated -> {
                log.rating = rated.rating();
                log.policyRow = rated.row();
            }
            case RatingDecision.Held held -> {
                log.holdReason = held.reason();
                log.policyRow = held.row();
            }
            case RatingDecision.NotEvaluated ignored ->
                    throw new IllegalArgumentException("평가 대상이 아닌 시도는 복습 기록을 남기지 않습니다.");
        }
        log.policyVersion = decision.policyVersion();
        log.userId = context.userId();
        log.memoryItemId = context.memoryItemId();
        log.questionId = context.questionId();
        log.attemptId = context.attemptId();
        log.reviewedAt = context.reviewedAt();
        log.predictedRetrievability = context.predictedRetrievability();
        log.priorAidExposed = context.priorAidExposed();
        log.elapsedSincePriorMs = context.elapsedSincePriorMs();
        log.sameDayRecheck = context.sameDayRecheck();
        log.failures = context.failures() == null || context.failures().isEmpty() ? null : String.join(",", context.failures());
        log.firstInputMs = context.firstInputMs();
        log.questionType = input.questionType();
        log.attemptKind = input.attempt();
        log.verdict = input.verdict();
        log.verdictConfidence = input.verdictConfidence();
        log.misread = input.misread();
        log.misreadConfidence = input.misreadConfidence();
        log.guessSuspected = input.guessSuspected();
        log.selfAssessment = input.selfAssessment();
        log.responseTimeMs = input.responseTime() == null ? 0 : input.responseTime().toMillis();
        log.elapsedDays = elapsedDays;
        log.parametersVersion = parametersVersion;
        return log;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getMemoryItemId() {
        return memoryItemId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Double getElapsedDays() {
        return elapsedDays;
    }

    public Rating getRating() {
        return rating;
    }

    public HoldReason getHoldReason() {
        return holdReason;
    }

    public int getPolicyRow() {
        return policyRow;
    }

    public int getPolicyVersion() {
        return policyVersion;
    }

    public Integer getParametersVersion() {
        return parametersVersion;
    }

    public Double getPredictedRetrievability() {
        return predictedRetrievability;
    }

    public AttemptKind getAttemptKind() {
        return attemptKind;
    }

    public boolean isPriorAidExposed() {
        return priorAidExposed;
    }

    public Long getElapsedSincePriorMs() {
        return elapsedSincePriorMs;
    }

    public boolean isSameDayRecheck() {
        return sameDayRecheck;
    }

    public AnswerVerdict getVerdict() {
        return verdict;
    }

    public double getVerdictConfidence() {
        return verdictConfidence;
    }

    public boolean isMisread() {
        return misread;
    }

    public double getMisreadConfidence() {
        return misreadConfidence;
    }

    public String getFailures() {
        return failures;
    }

    public boolean isGuessSuspected() {
        return guessSuspected;
    }

    public SelfAssessment getSelfAssessment() {
        return selfAssessment;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public Long getFirstInputMs() {
        return firstInputMs;
    }
}
