package com.khack.review.common.application.port.out;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 JevPort. HTTP 없이 미리 정한 결과를 돌려주고, 받은 요청을 기록한다.
 *
 * <pre>{@code
 * var jev = new FakeJevPort().willReturn(new JevResult("fake",
 *         Map.of("status", new JevAnswer.Choice("correct", Map.of("correct", 1.0), 0.3))));
 * }</pre>
 */
public class FakeJevPort implements JevPort {

	public record Call(Object state, Map<String, JevQuestion> questions) {
	}

	private final List<Call> calls = new ArrayList<>();
	private JevResult next;
	private RuntimeException failure;

	public FakeJevPort willReturn(JevResult result) {
		this.next = result;
		this.failure = null;
		return this;
	}

	public FakeJevPort willFail(RuntimeException failure) {
		this.failure = failure;
		return this;
	}

	public List<Call> calls() {
		return List.copyOf(calls);
	}

	@Override
	public JevResult evaluate(Object state, Map<String, JevQuestion> questions) {
		calls.add(new Call(state, Map.copyOf(questions)));
		if (failure != null) {
			throw failure;
		}
		if (next == null) {
			throw new IllegalStateException("FakeJevPort: willReturn()으로 결과를 먼저 지정하세요");
		}
		return next;
	}

}
