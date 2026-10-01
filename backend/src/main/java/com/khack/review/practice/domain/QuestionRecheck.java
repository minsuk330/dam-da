package com.khack.review.practice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 모호해서 보류된 문제의 재검사 기록(스펙 §6.4.5). 문제마다 한 번만 재검사하므로 문제 ID가 고유하다. 실패한 기록만 다시 시도한다.
 */
@Entity
@Table(name = "question_recheck")
public class QuestionRecheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long questionId;

    @Column(nullable = false)
    private Long userId;

    /** 재검사를 일으킨 보류 제시. */
    @Column(nullable = false)
    private Long presentationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecheckResult result;

    /** 보류된 문제 대신 다시 확인에 쓸 변형 문제(통과·폐기 모두). 못 만들었으면 비어 있다. */
    private Long variantQuestionId;

    @Column(length = 1_000)
    private String failure;

    @Column(nullable = false)
    private Instant checkedAt;

    protected QuestionRecheck() {
    }

    public QuestionRecheck(Long questionId, Long userId, Long presentationId) {
        this.questionId = questionId;
        this.userId = userId;
        this.presentationId = presentationId;
    }

    public void record(RecheckResult result, @Nullable Long variantQuestionId, @Nullable String failure, Instant at) {
        this.result = result;
        this.variantQuestionId = variantQuestionId;
        this.failure = failure == null || failure.length() <= 1_000 ? failure : failure.substring(0, 1_000);
        this.checkedAt = at;
    }

    /** 다시 확인에 쓸 문제. 보류된 같은 문제는 다시 내지 않으므로 변형 문제만 쓴다. 없거나 실패면 비어 있다. */
    public Optional<Long> usableQuestionId() {
        return result == RecheckResult.FAILED ? Optional.empty() : Optional.ofNullable(variantQuestionId);
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public RecheckResult getResult() {
        return result;
    }

    public Long getVariantQuestionId() {
        return variantQuestionId;
    }

    public String getFailure() {
        return failure;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
