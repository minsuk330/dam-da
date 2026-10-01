package com.khack.review.practice.domain;

/** 한 제시의 풀이 경로 (스펙 §7 6단계). 지식 상태 표시와 피드백에만 쓰고 FSRS 등급에는 영향을 주지 않는다. */
public enum FeedbackPath {
    /** 도움 없이 정답. */
    INDEPENDENT,
    /** 힌트 후 정답. */
    AFTER_HINT,
    /** 설명 후 정답. */
    AFTER_EXPLANATION,
    /** 반복 오답. */
    REPEATED_WRONG
}
