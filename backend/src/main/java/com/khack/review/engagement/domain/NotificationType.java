package com.khack.review.engagement.domain;

/** 앱 안 알림 종류 (스펙 §7.2, §7.7). */
public enum NotificationType {
    /** 학습 내용이 도착해 확인할 수 있다. */
    SESSION_READY,
    /** 매일 학습 시간이다 (#23). */
    DAILY_LEARNING
}
