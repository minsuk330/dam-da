package com.khack.review.practice.domain;

import com.khack.review.question.domain.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 첫 학습 계획에 저장된 문제 1개. 문제 생성(#15)이 이 목록으로 문제를 만든다. */
@Embeddable
public class PlannedQuestionEntry {

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningGoal goal;

    @Column(nullable = false)
    private Long memoryItemId;

    private Long relatedItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    protected PlannedQuestionEntry() {
    }

    PlannedQuestionEntry(int position, FirstStudyComposer.PlannedQuestion question) {
        this.position = position;
        this.goal = question.goal();
        this.memoryItemId = question.memoryItemId();
        this.relatedItemId = question.relatedItemId();
        this.questionType = question.type();
    }

    public int getPosition() {
        return position;
    }

    public LearningGoal getGoal() {
        return goal;
    }

    public Long getMemoryItemId() {
        return memoryItemId;
    }

    public Long getRelatedItemId() {
        return relatedItemId;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }
}
