package com.khack.review.analysis.domain;

/** 학습 세션의 분야를 자동으로 정했다 (스펙 §7.10). {@code code}는 분류표의 소분류 코드다. */
public record LearningSessionFieldClassified(Long sessionId, String code) {
}
