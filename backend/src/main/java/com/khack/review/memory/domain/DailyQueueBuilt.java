package com.khack.review.memory.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 매일 학습 큐를 만들었다(스펙 §8.4 `DailyQueueBuilt`). {@code itemIds}는 큐 순서(복습 먼저, 신규 뒤)이고,
 * {@code carriedOver}는 예산·하루 1개·신규 상한 때문에 오늘 편성하지 못해 다음 큐로 넘어간 후보 수다.
 */
public record DailyQueueBuilt(Long userId, LocalDate date, List<Long> itemIds, int reviewCount, int newCount,
        Duration estimatedTime, int carriedOver) {
}
