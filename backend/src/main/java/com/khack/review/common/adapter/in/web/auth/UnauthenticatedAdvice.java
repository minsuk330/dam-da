package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.UnauthenticatedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 보안 필터를 지나서도 사용자를 알 수 없으면(설정 실수 등) 500 대신 401로 응답한다. */
@RestControllerAdvice
class UnauthenticatedAdvice {

    record ErrorResponse(String code, String message) {
    }

    @ExceptionHandler(UnauthenticatedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse unauthenticated(UnauthenticatedException e) {
        return new ErrorResponse("unauthenticated", e.getMessage());
    }
}
