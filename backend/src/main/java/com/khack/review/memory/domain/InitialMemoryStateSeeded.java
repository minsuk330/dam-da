package com.khack.review.memory.domain;

import java.util.List;

/**
 * 확인된 학습 세션의 기억 항목에 대화 신호로 초기 평가를 반영했다(스펙 §8.4 `InitialMemoryStateSeeded`).
 * {@code unrated}는 신호가 없어 첫 풀이를 기다리는 신규 항목이다.
 */
public record InitialMemoryStateSeeded(Long sessionId, List<Long> again, List<Long> good, List<Long> unrated) {
}
