package com.khack.review.common.application.port.out;

/**
 * 앱인토스 토스 로그인(#149). 미니앱의 {@code appLogin()}이 준 인가 코드를 이 앱의 토스 사용자로 바꾼다.
 * 구현은 mTLS 인증서로 앱인토스 서버를 부른다(토큰 발급 → 사용자 정보 조회).
 */
public interface TossLoginPort {

	/**
	 * 인가 코드(10분 유효, 1회용)를 토스 사용자로 바꾼다. {@code referrer}는 {@code appLogin()}이 준 값({@code DEFAULT}·{@code SANDBOX})이다.
	 * 토스가 거절하거나 호출에 실패하면 {@link TossLoginException}을 던진다.
	 */
	TossUser login(String authorizationCode, String referrer);

	/**
	 * 이 앱과 토스 사용자의 로그인 연결을 끊는다(회원 탈퇴). 다음에 들어오면 토스 약관 동의부터 다시 한다.
	 * 실패하면 {@link TossLoginException}을 던진다. 직접 끊은 경우에는 토스가 연결 끊기 콜백을 보내지 않는다.
	 */
	void disconnect(String userKey);

	/** {@code userKey}는 이 미니앱에서만 쓰이는 토스 사용자 식별자다. */
	record TossUser(String userKey) {
	}

}
