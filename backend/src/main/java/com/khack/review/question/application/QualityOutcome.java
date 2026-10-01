package com.khack.review.question.application;

/** 품질 검사 결과. 통과하지 못하면 그 문제는 사용자에게 보이지 않는다. */
public record QualityOutcome(boolean approved, String note) {
}
