package com.khack.review.practice.domain;

/** `not_met`의 이유 (스펙 §6.4.5 Jev 출력 `failures`). 화면 표시·오개념 기록의 대표 이유는 contradiction > omission 순이다. */
public enum AnswerFailure {
    /** 평가 대상 기준을 부정하거나 틀리게 주장 */
    CONTRADICTION,
    /** 필수 내용 누락 */
    OMISSION,
    /** 질문을 다르게 이해함 */
    MISREAD
}
