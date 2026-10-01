package com.khack.review.practice.domain;

/** 답변 판정 상태. 실패하면 기억 상태를 바꾸지 않고 보류로 둔다(docs/jev.md 오류와 재시도). */
public enum JudgmentStatus {
    JUDGED,
    FAILED
}
