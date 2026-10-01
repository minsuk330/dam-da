package com.khack.review.analysis.domain;

/**
 * 복습 단위 검수를 마치고 사용자 확인을 기다린다(세션이 확인 대기가 됨). 상태 변경과 같은 트랜잭션에서 발행된다.
 * 앱 안 알림("학습 내용이 도착했어요")이 이 이벤트로 만들어진다.
 */
public record LearningSessionReadyForConfirmation(Long sessionId, Long userId, String topicHint) {
}
