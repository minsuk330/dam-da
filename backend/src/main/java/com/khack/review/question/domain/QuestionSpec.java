package com.khack.review.question.domain;

import org.jspecify.annotations.Nullable;

/**
 * 만들 문제 1개의 명세. 첫 학습은 계획(§7.8)에서 오고, {@code relatedItemId}는 개념 구분하기의 비교 대상이다.
 * 학습 목표는 practice와 순환 참조하지 않도록 이름 문자열로 받는다.
 */
public record QuestionSpec(int position, Long memoryItemId, @Nullable Long relatedItemId, QuestionType type,
        @Nullable String learningGoal) {
}
