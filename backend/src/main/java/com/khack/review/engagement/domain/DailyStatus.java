package com.khack.review.engagement.domain;

/** 하루의 매일 학습 결과 (스펙 §7.7). 기록이 없는 지난 날은 학습하지 않은 날이다. */
public enum DailyStatus {
    /** 그날의 매일 학습 큐를 끝냈다. 연속 일수 +1. */
    COMPLETED,
    /** 복습할 항목이 없어 큐가 비었다. 연속 기록을 끊지 않는다. */
    EMPTY
}
