package com.khack.review.practice.domain;

/** 답을 제출할 때 고르는 자기평가 (스펙 §6.4.5). */
public enum SelfAssessment {
    /** 쉽게 떠올렸음 */
    EASY,
    /** 힘들게 떠올렸거나 확신이 약함 */
    EFFORTFUL,
    /** 떠올리지 못하고 추측했음 */
    GUESSED
}
