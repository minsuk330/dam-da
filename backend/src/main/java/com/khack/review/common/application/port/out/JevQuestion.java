package com.khack.review.common.application.port.out;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Jev 질문. {@code instructions}는 문자열 또는 구조화 값(record·Map)이다.
 */
public sealed interface JevQuestion {

	Object instructions();

	/** 예/아니오. 답은 "예"일 확률(0~1). */
	record Noul(Object instructions, @Nullable String whenTrue, @Nullable String whenFalse) implements JevQuestion {
	}

	/** 정해진 선택지 중 하나. options는 선택지 → 설명(설명 없으면 null). 최대 255개. */
	record Choice(Object instructions, Map<String, @Nullable String> options) implements JevQuestion {
	}

	/** 순서가 있는 단계(2~10개)에 대한 점수. 답은 0부터 시작하는 가중 평균 점수. */
	record Score(Object instructions, List<String> levels) implements JevQuestion {
	}

	static Noul noul(Object instructions) {
		return new Noul(instructions, null, null);
	}

	static Noul noul(Object instructions, String whenTrue, String whenFalse) {
		return new Noul(instructions, whenTrue, whenFalse);
	}

	static Choice choice(Object instructions, Map<String, @Nullable String> options) {
		return new Choice(instructions, options);
	}

	static Score score(Object instructions, List<String> levels) {
		return new Score(instructions, levels);
	}

}
