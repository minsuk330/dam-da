package com.khack.review.common.adapter.in.web;

import com.khack.review.common.application.RateLimitedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 요청 한도를 넘으면 429와 사용자에게 보여 줄 문구로 응답한다(#184). */
@RestControllerAdvice
class RateLimitAdvice {

    record RateLimitResponse(String code, String message) {
    }

    @ExceptionHandler(RateLimitedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    RateLimitResponse rateLimited(RateLimitedException e) {
        return new RateLimitResponse("rate_limited", e.getMessage());
    }
}
