package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.AgreementRequiredException;
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

    /** 토스 익명 계정이 약관 동의 전에 대화를 저장하려 했다(#149). 앱은 동의 카드를 보여준다. */
    @ExceptionHandler(AgreementRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorResponse agreementRequired(AgreementRequiredException e) {
        return new ErrorResponse("agreement_required", e.getMessage());
    }
}
