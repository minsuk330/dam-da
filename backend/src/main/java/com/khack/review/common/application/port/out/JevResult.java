package com.khack.review.common.application.port.out;

import java.util.Map;

/**
 * 질문 이름 → 답. {@code model}은 실제로 판정한 모델 버전이다.
 */
public record JevResult(String model, Map<String, JevAnswer> answers) {

	public JevAnswer.Noul noul(String name) {
		return answer(name, JevAnswer.Noul.class);
	}

	public JevAnswer.Choice choice(String name) {
		return answer(name, JevAnswer.Choice.class);
	}

	public JevAnswer.Score score(String name) {
		return answer(name, JevAnswer.Score.class);
	}

	private <T extends JevAnswer> T answer(String name, Class<T> type) {
		JevAnswer answer = answers.get(name);
		if (answer == null) {
			throw new IllegalArgumentException("Jev 답 없음: " + name);
		}
		if (!type.isInstance(answer)) {
			throw new IllegalArgumentException("Jev 답 타입 불일치: " + name + " 은(는) "
					+ answer.getClass().getSimpleName() + ", 요청 " + type.getSimpleName());
		}
		return type.cast(answer);
	}

}
