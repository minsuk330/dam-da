package com.khack.review.common.application;

/** 로그인이 필요한 곳에 인증이 없다. 웹에서는 401로 응답한다. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("로그인이 필요합니다.");
    }
}
