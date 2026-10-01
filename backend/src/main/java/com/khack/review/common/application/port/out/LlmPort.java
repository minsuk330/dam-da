package com.khack.review.common.application.port.out;

/**
 * 자유 형식 생성 작업(문제·힌트·설명·변형 문제, 공유 링크 입력 추출)을 위한 LLM 포트.
 * 판정(Jev)이나 복습 시점 계산에는 쓰지 않는다.
 */
public interface LlmPort {

	/**
	 * 모델이 답하기 전에 하는 추론의 양. 학습자가 화면에서 기다리는 짧은 생성(힌트·설명)은 {@link #MINIMAL}로 지연을 줄이고,
	 * 품질이 중요한 생성(문제·추출)은 {@link #DEFAULT}로 모델 기본값을 쓴다.
	 */
	enum Reasoning {
		DEFAULT, MINIMAL
	}

	String generate(String systemPrompt, String userPrompt);

	default <T> T generate(String systemPrompt, String userPrompt, Class<T> responseType) {
		return generate(systemPrompt, userPrompt, responseType, Reasoning.DEFAULT);
	}

	<T> T generate(String systemPrompt, String userPrompt, Class<T> responseType, Reasoning reasoning);

}
