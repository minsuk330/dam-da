package com.khack.review.question.domain;

/** 문제 상태. 승인된 문제만 사용자에게 보인다(규칙 3). */
public enum QuestionStatus {
    /** 생성됐고 품질 검사 전이다. */
    CANDIDATE,
    /** 품질 검사를 통과했다. */
    APPROVED,
    /** 품질 검사에서 떨어졌다. */
    REJECTED,
    /** 승인됐지만 풀이에서 모호하다고 보류된 뒤 재검사에서 떨어져 더 쓰지 않는다. */
    RETIRED
}
