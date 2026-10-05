package com.khack.review.common.application.port.out;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 TossLoginPort. 인가 코드마다 돌려줄 userKey를 정한다. 정하지 않은 코드는 거절한다.
 *
 * <pre>{@code
 * toss.willAccept("code-1", "443731104");
 * }</pre>
 */
public class FakeTossLoginPort implements TossLoginPort {

	private final Map<String, String> userKeys = new HashMap<>();
	private final Map<String, String> anonKeys = new HashMap<>();

	/** {@link #disconnect}로 받은 userKey. */
	public final List<String> disconnected = new CopyOnWriteArrayList<>();

	public FakeTossLoginPort willAccept(String authorizationCode, String userKey) {
		userKeys.put(authorizationCode, userKey);
		return this;
	}

	@Override
	public TossUser login(String authorizationCode, String referrer) {
		String userKey = userKeys.get(authorizationCode);
		if (userKey == null) {
			throw new TossLoginException("invalid_grant");
		}
		return new TossUser(userKey);
	}

	/** 익명 식별키 인증 코드마다 돌려줄 anonKey를 정한다. 정하지 않은 코드는 거절한다. */
	public FakeTossLoginPort willExchangeAnonymous(String code, String anonKey) {
		anonKeys.put(code, anonKey);
		return this;
	}

	@Override
	public String anonymousKey(String code) {
		String anonKey = anonKeys.get(code);
		if (anonKey == null) {
			throw new TossLoginException("4011");
		}
		return anonKey;
	}

	@Override
	public void disconnect(String userKey) {
		disconnected.add(userKey);
	}

}
