package com.khack.review.analysis.application;

/** 학습 분야 판정 결과. {@code code}는 항상 분류표의 소분류 코드이고 {@code reason}은 판정 근거다. */
public record SessionFieldOutcome(String code, String reason) {
}
