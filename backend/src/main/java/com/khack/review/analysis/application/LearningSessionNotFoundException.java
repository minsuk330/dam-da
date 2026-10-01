package com.khack.review.analysis.application;

/** 학습 세션이 없거나 현재 사용자의 것이 아니다. */
public class LearningSessionNotFoundException extends RuntimeException {

    public LearningSessionNotFoundException(Long sessionId) {
        super("학습을 찾을 수 없어요.");
    }
}
