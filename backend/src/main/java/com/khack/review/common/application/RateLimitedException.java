package com.khack.review.common.application;

/** 요청 한도를 넘었다(#184). 웹에서는 429로 응답한다. 메시지는 사용자에게 그대로 보여 준다. */
public class RateLimitedException extends RuntimeException {

    public RateLimitedException(String message) {
        super(message);
    }
}
