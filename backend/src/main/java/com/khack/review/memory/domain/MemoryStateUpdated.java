package com.khack.review.memory.domain;

import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.State;
import java.time.Instant;

/** 등급을 FSRS에 반영해 기억 상태가 바뀌었다(스펙 §8.4 `MemoryStateUpdated`). */
public record MemoryStateUpdated(Long userId, Long memoryItemId, Rating rating, Instant reviewedAt, State state,
        double stability, double difficulty) {
}
