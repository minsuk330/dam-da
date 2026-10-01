package com.khack.review.collection.domain;

import java.time.Instant;

/**
 * 학습 대화를 검증해 저장했다(스펙 §8.4 `ConversationSubmitted`). 저장과 같은 트랜잭션에서 발행된다.
 * {@code input}은 검증을 통과한 커넥터 스키마 v5 값이다.
 */
public record ConversationSubmitted(Long conversationId, Long userId, SessionInput input, Instant receivedAt) {
}
