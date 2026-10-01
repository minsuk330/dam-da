package com.khack.review.analysis.domain;

/** 학습 분야 라벨을 누가 정했는가 (스펙 §7.10). 사용자가 고른 값은 자동 판정이 덮어쓰지 않는다(규칙 19). */
public enum FieldSource {
    AUTO,
    USER
}
