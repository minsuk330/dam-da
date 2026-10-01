package com.khack.review.practice.domain;

import com.khack.review.question.domain.QuestionType;
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
 * 답변 제출 1회 (스펙 §6.4.5 기록). 판정(Jev)·등급 변환·FSRS 갱신은 이 기록을 읽어 따로 한다.
 * 객관식은 제출할 때 코드로 채점해 {@code choiceCorrect}에 둔다.
 */
@Entity
@Table(name = "practice_attempt")
public class PracticeAttempt {

    public static final int MAX_ANSWER_LENGTH = 5_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long presentationId;

    @Column(nullable = false)
    private Long practiceSessionId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private Long memoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptKind kind;

    @Column(length = MAX_ANSWER_LENGTH)
    private String answerText;

    private Integer choiceIndex;

    /** 객관식 코드 채점 결과. 서술형이면 null(판정은 Jev). */
    private Boolean choiceCorrect;

    @Enumerated(EnumType.STRING)
    private SelfAssessment selfAssessment;

    @Column(nullable = false)
    private Instant submittedAt;

    /** 응답 시간. 클라이언트 측정값을 서버 경과 시간으로 상한한 값. */
    @Column(nullable = false)
    private long responseTimeMs;

    /** 서버 기준 경과 시간 (응답 시간을 재기 시작한 시각 → 제출). */
    @Column(nullable = false)
    private long serverElapsedMs;

    private Long firstInputMs;

    @Column(nullable = false)
    private boolean interpretationHelp;

    @Column(nullable = false)
    private boolean priorAidExposed;

    private Long elapsedSincePriorMs;

    @Column(nullable = false)
    private boolean sameDayRecheck;

    /** 답변 직전의 예측 R (제시 시점). */
    private Double predictedRetrievability;

    protected PracticeAttempt() {
    }

    public record Answer(@Nullable String text, @Nullable Integer choiceIndex, @Nullable Boolean choiceCorrect,
            @Nullable SelfAssessment selfAssessment) {
    }

    public record Timing(Instant submittedAt, long responseTimeMs, long serverElapsedMs, @Nullable Long firstInputMs) {
    }

    public static PracticeAttempt of(QuestionPresentation presentation, AttemptRules.Classification classification,
            Answer answer, Timing timing) {
        PracticeAttempt attempt = new PracticeAttempt();
        attempt.presentationId = presentation.getId();
        attempt.practiceSessionId = presentation.getPracticeSessionId();
        attempt.userId = presentation.getUserId();
        attempt.questionId = presentation.getQuestionId();
        attempt.memoryItemId = presentation.getMemoryItemId();
        attempt.questionType = presentation.getQuestionType();
        attempt.kind = classification.kind();
        attempt.answerText = answer.text();
        attempt.choiceIndex = answer.choiceIndex();
        attempt.choiceCorrect = answer.choiceCorrect();
        attempt.selfAssessment = answer.selfAssessment();
        attempt.submittedAt = timing.submittedAt();
        attempt.responseTimeMs = timing.responseTimeMs();
        attempt.serverElapsedMs = timing.serverElapsedMs();
        attempt.firstInputMs = timing.firstInputMs();
        attempt.interpretationHelp = classification.interpretationHelp();
        attempt.priorAidExposed = classification.priorAidExposed();
        attempt.elapsedSincePriorMs = classification.sincePrior() == null ? null : classification.sincePrior().toMillis();
        attempt.sameDayRecheck = presentation.isSameDayRecheck();
        attempt.predictedRetrievability = presentation.getPredictedRetrievability();
        return attempt;
    }

    public Long getId() {
        return id;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public Long getPracticeSessionId() {
        return practiceSessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getMemoryItemId() {
        return memoryItemId;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public AttemptKind getKind() {
        return kind;
    }

    public String getAnswerText() {
        return answerText;
    }

    public Integer getChoiceIndex() {
        return choiceIndex;
    }

    public Boolean getChoiceCorrect() {
        return choiceCorrect;
    }

    public SelfAssessment getSelfAssessment() {
        return selfAssessment;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public long getServerElapsedMs() {
        return serverElapsedMs;
    }

    public Long getFirstInputMs() {
        return firstInputMs;
    }

    public boolean isInterpretationHelp() {
        return interpretationHelp;
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

    public Double getPredictedRetrievability() {
        return predictedRetrievability;
    }
}
