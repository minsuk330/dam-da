package com.khack.review.analysis.domain;

/**
 * 학습 대화로 학습 세션을 만들었다. 세션 저장과 같은 트랜잭션에서 발행되고, 복습 단위 검수는 커밋 뒤에 시작한다.
 */
public record LearningSessionCreated(Long sessionId) {
}
