package com.khack.review.common.application.port.out;

/**
 * Jev 호출 실패. {@code status}는 HTTP 상태 코드이며, 연결 오류처럼 응답이 없으면 0이다.
 * 429(한도 초과)·529(과부하)는 {@link #retryable()}이 true다.
 */
public class JevCallException extends RuntimeException {

	private final int status;

	public JevCallException(int status, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	public int status() {
		return status;
	}

	public boolean retryable() {
		return status == 429 || status == 529;
	}

}
