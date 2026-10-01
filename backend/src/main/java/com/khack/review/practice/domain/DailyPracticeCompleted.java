package com.khack.review.practice.domain;

import java.time.LocalDate;

/**
 * 오늘의 학습 큐를 끝냈다(스펙 §7.7). {@code date}는 그 풀이를 시작한 날로, 자정을 넘겨 끝내도 시작한 날의 학습이다.
 * 같은 날 재확인으로 다시 열렸다가 끝나도 처음 한 번만 발행한다.
 */
public record DailyPracticeCompleted(Long userId, Long practiceId, LocalDate date) {
}
