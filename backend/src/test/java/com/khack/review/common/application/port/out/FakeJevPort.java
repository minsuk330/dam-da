package com.khack.review.common.application.port.out;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 JevPort. HTTP 없이 미리 정한 결과를 돌려주고, 받은 요청을 기록한다.
 *
 * <pre>{@code
 * var jev = new FakeJevPort().willReturn(new JevResult("fake",
 *         Map.of("status", new JevAnswer.Choice("correct", Map.of("correct", 1.0), 0.3))));
 * }</pre>
 *
 * 재시도처럼 호출마다 다른 응답이 필요하면 {@link #willRespond}로 순서대로 지정한다.
 */
public class FakeJevPort implements JevPort {

	public record Call(Object state, Map<String, JevQuestion> questions) {
	}

	private final List<Call> calls = new ArrayList<>();
	private final Deque<Object> queued = new ArrayDeque<>();
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

	/** 호출마다 앞에서부터 하나씩 쓴다. {@link JevResult}면 돌려주고 {@link RuntimeException}이면 던진다. 다 쓰면 willReturn/willFail로 돌아간다. */
	public FakeJevPort willRespond(Object... responses) {
		for (Object response : responses) {
			if (!(response instanceof JevResult) && !(response instanceof RuntimeException)) {
				throw new IllegalArgumentException("JevResult 또는 RuntimeException만 지정할 수 있습니다: " + response);
			}
			queued.add(response);
		}
		return this;
	}

	public List<Call> calls() {
		return List.copyOf(calls);
	}

	@Override
	public JevResult evaluate(Object state, Map<String, JevQuestion> questions) {
		calls.add(new Call(state, Map.copyOf(questions)));
		Object response = queued.poll();
		if (response instanceof RuntimeException e) {
			throw e;
		}
		if (response instanceof JevResult result) {
			return result;
		}
		if (failure != null) {
			throw failure;
		}
		if (next == null) {
			throw new IllegalStateException("FakeJevPort: willReturn()으로 결과를 먼저 지정하세요");
		}
		return next;
	}

}
