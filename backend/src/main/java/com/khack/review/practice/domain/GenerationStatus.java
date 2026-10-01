package com.khack.review.practice.domain;

/** 첫 학습 문제 생성 상태. 실패하면 세션은 확인 완료에 머물러 목표를 다시 고를 수 있고, 다시 고르면 생성을 다시 시작한다. */
public enum GenerationStatus {
    GENERATING,
    READY,
    FAILED
}
