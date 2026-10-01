package com.khack.review.common.application.port.out;

/**
 * 자유 형식 생성 작업(문제·힌트·설명·변형 문제, 공유 링크 입력 추출)을 위한 LLM 포트.
 * 판정(Jev)이나 복습 시점 계산에는 쓰지 않는다.
 */
public interface LlmPort {

	String generate(String systemPrompt, String userPrompt);

	<T> T generate(String systemPrompt, String userPrompt, Class<T> responseType);

}
