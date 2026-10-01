package com.khack.review.analysis.domain;

/** 기억 항목의 학습 상태. 첫 풀이 전에는 신규다(스펙 §6.4.4). */
public enum MemoryItemStatus {
    /** 아직 첫 풀이가 없다. */
    NEW,
    /** 첫 풀이 이후 복습 대상이다. */
    ACTIVE,
    /** 사용자가 제외했다. */
    EXCLUDED
}
