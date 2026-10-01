package com.khack.review.analysis.domain;

/**
 * 사용자가 앱에서 발화와 복습 단위 확인을 마쳤다(스펙 §8.4 `ReviewUnitsConfirmedByUser`). 확인 완료와 같은 트랜잭션에서 발행된다.
 * 초기 평가(§6.4.2)와 문제 생성은 이 뒤에 한다(규칙 12, 18).
 */
public record ReviewUnitsConfirmedByUser(Long sessionId) {
}
