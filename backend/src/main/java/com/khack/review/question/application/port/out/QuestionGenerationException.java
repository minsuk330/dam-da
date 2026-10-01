package com.khack.review.question.application.port.out;

/** 문제를 만들지 못했다. 서버는 재생성 횟수 안에서 다시 시도하고, 넘으면 해당 항목 출제를 보류한다. */
public class QuestionGenerationException extends RuntimeException {

    public QuestionGenerationException(String message) {
        super(message);
    }

    public QuestionGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
