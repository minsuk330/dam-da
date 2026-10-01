package com.khack.review.practice.application.port.out;

/** 피드백 내용을 만들지 못했다. */
public class FeedbackGenerationException extends RuntimeException {

    public FeedbackGenerationException(String message) {
        super(message);
    }

    public FeedbackGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
