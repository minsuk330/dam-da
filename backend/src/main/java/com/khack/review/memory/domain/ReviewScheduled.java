package com.khack.review.memory.domain;

import java.time.Instant;

/** FSRS가 다음 복습 시각을 정했다(스펙 §8.4 `ReviewScheduled`). 매일 학습 큐가 이 시각과 R로 후보를 고른다. */
public record ReviewScheduled(Long userId, Long memoryItemId, Instant due) {
}
