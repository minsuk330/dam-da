package com.khack.review.practice.domain;

/** 다음 행동 (스펙 §6.2). {@code RELEARN_TODAY}는 오늘 안에 다시 묻는 것만 뜻하고 하루를 넘는 시점은 FSRS가 정한다. */
public enum FeedbackAction {
    ADVANCE,
    RETRY,
    GIVE_HINT,
    EXPLAIN_CONCEPT,
    GENERATE_VARIANT,
    RELEARN_TODAY,
    REQUEST_CONFIRMATION
}
