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
 * 문제 제시 1회 = 복습 기회 1회 (스펙 §6.4.5). 제시 시각과 답변 직전의 예측 R을 남긴다.
 * R은 제시 시점에 FSRS로 계산하며, 아직 등급을 받은 적 없는 항목이면 null이다.
 */
@Entity
@Table(name = "question_presentation")
public class QuestionPresentation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long practiceSessionId;

    @Column(nullable = false)
    private Long userId;

    /** 풀이 세션 큐의 순서. */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private Long memoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    @Column(nullable = false)
    private Instant presentedAt;

    private Double predictedRetrievability;

    /** 같은 날 재확인이면 원래 제시. */
    private Long recheckOfPresentationId;

    protected QuestionPresentation() {
    }

    public QuestionPresentation(Long practiceSessionId, Long userId, int position, Long questionId, Long memoryItemId,
            QuestionType questionType, Instant presentedAt, @Nullable Double predictedRetrievability,
            @Nullable Long recheckOfPresentationId) {
        this.practiceSessionId = practiceSessionId;
        this.userId = userId;
        this.position = position;
        this.questionId = questionId;
        this.memoryItemId = memoryItemId;
        this.questionType = questionType;
        this.presentedAt = presentedAt;
        this.predictedRetrievability = predictedRetrievability;
        this.recheckOfPresentationId = recheckOfPresentationId;
    }

    public boolean isSameDayRecheck() {
        return recheckOfPresentationId != null;
    }

    public Long getId() {
        return id;
    }

    public Long getPracticeSessionId() {
        return practiceSessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public int getPosition() {
        return position;
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

    public Instant getPresentedAt() {
        return presentedAt;
    }

    public Double getPredictedRetrievability() {
        return predictedRetrievability;
    }

    public Long getRecheckOfPresentationId() {
        return recheckOfPresentationId;
    }
}
