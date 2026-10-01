package com.khack.review.question.domain;

/**
 * CANDIDATE는 품질 검사(Jev) 전이라 사용자에게 보여주지 않는다(스펙 규칙 3). APPROVED만 출제한다.
 */
public enum QuestionStatus {
    CANDIDATE, APPROVED, REJECTED
}
