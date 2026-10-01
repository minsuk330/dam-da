package com.khack.review.practice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 풀이 세션에서 낼 문제 1개. 같은 날 재확인이면 원래 제시를 가리킨다. */
@Embeddable
public class PracticeQueueEntry {

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private Long questionId;

    private Long recheckOfPresentationId;

    protected PracticeQueueEntry() {
    }

    PracticeQueueEntry(int position, Long questionId, Long recheckOfPresentationId) {
        this.position = position;
        this.questionId = questionId;
        this.recheckOfPresentationId = recheckOfPresentationId;
    }

    public int getPosition() {
        return position;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getRecheckOfPresentationId() {
        return recheckOfPresentationId;
    }
}
