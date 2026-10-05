package com.khack.review.common.application.port.out;

/** 토스 로그인 실패. 인가 코드 만료·재사용, 토스 오류 응답, 연결 실패를 모두 포함한다. 메시지는 로그용이다. */
public class TossLoginException extends RuntimeException {

	public TossLoginException(String message) {
		super(message);
	}

	public TossLoginException(String message, Throwable cause) {
		super(message, cause);
	}

}
