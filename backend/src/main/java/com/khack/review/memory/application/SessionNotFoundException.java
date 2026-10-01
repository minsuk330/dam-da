package com.khack.review.memory.application;

/** 사용자의 학습 세션이 아니거나 없다. */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(Long sessionId) {
        super("학습 세션 없음: " + sessionId);
    }
}
