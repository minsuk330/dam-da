package com.khack.review.common.application.port.out;

import java.util.HashMap;
import java.util.Map;

/**
 * 테스트용 TossLoginPort. 인가 코드마다 돌려줄 userKey를 정한다. 정하지 않은 코드는 거절한다.
 *
 * <pre>{@code
 * toss.willAccept("code-1", "443731104");
 * }</pre>
 */
public class FakeTossLoginPort implements TossLoginPort {

	private final Map<String, String> userKeys = new HashMap<>();

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

}
