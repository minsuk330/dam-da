package com.khack.review.engagement.domain;

import java.time.LocalDate;

/** 매일 학습을 끝내 연속 학습 일수가 갱신됐다(스펙 §8.4 `StreakUpdated`). */
public record StreakUpdated(Long userId, LocalDate date, int current, int best) {
}
