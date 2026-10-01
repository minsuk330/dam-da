package com.khack.review.common.application.port.out;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 테스트용 LlmPort. 실제 호출 없이 미리 정한 응답을 차례로 돌려주고, 받은 프롬프트를 기록한다.
 * 응답 자리에 RuntimeException을 넣으면 그 호출은 실패한다.
 *
 * <pre>{@code
 * var llm = new FakeLlmPort().willReturn(new GeneratedQuestions(List.of(...)));
 * }</pre>
 */
public class FakeLlmPort implements LlmPort {

	public record Call(String systemPrompt, String userPrompt) {
	}

	private final List<Call> calls = new ArrayList<>();
	private final Deque<Object> responses = new ArrayDeque<>();

	public FakeLlmPort willReturn(Object... responses) {
		this.responses.addAll(List.of(responses));
		return this;
	}

	public List<Call> calls() {
		return List.copyOf(calls);
	}

	@Override
	public String generate(String systemPrompt, String userPrompt) {
		return generate(systemPrompt, userPrompt, String.class);
	}

	@Override
	public <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
		calls.add(new Call(systemPrompt, userPrompt));
		if (responses.isEmpty()) {
			throw new IllegalStateException("FakeLlmPort: willReturn()으로 응답을 먼저 지정하세요");
		}
		Object next = responses.poll();
		if (next instanceof RuntimeException failure) {
			throw failure;
		}
		return responseType.cast(next);
	}

}
