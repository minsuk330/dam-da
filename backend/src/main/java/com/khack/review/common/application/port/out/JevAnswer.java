package com.khack.review.common.application.port.out;

import java.util.Map;

/**
 * Jev 답. Choice·Score는 확률 분포에서 계산한 신뢰도(0~1)를 함께 가진다.
 */
public sealed interface JevAnswer {

	record Noul(double probability) implements JevAnswer {
	}

	record Choice(String choice, Map<String, Double> probabilities, double confidence) implements JevAnswer {
	}

	/** levels는 단계 번호("0", "1", ...) → 단계 설명. */
	record Score(double score, Map<String, String> levels, Map<String, Double> probabilities, double confidence)
			implements JevAnswer {
	}

}
