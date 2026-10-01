package com.khack.review.common.application.port.out;

import java.util.Map;

/**
 * 정해진 선택지 안의 구조화 판정(품질 검사, 답변 판정, 다음 행동 선택)을 위한 Jev 포트.
 * 생성 작업은 {@link LlmPort}, 복습 시점 계산은 FSRS가 맡는다. 신뢰도 기준 적용은 호출하는 쪽이 한다.
 */
public interface JevPort {

	/**
	 * @param state     판정 대상. 문자열 또는 JSON으로 직렬화할 수 있는 record·Map·List
	 * @param questions 질문 이름 → 질문. 답은 같은 이름으로 돌아온다
	 * @throws JevCallException 호출 실패(인증, 요청 검증, 한도 초과, 과부하, 연결 오류)
	 */
	JevResult evaluate(Object state, Map<String, JevQuestion> questions);

}
