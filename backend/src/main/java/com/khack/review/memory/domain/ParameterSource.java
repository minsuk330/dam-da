package com.khack.review.memory.domain;

/** 매개변수 묶음의 출처 (스펙 §6.4.9). */
public enum ParameterSource {
    /** java-fsrs 기본값. */
    DEFAULT,
    /** 사용자 복습 기록으로 학습하고 검증한 값. */
    OPTIMIZED
}
