package com.khack.review.practice.domain;

import java.time.Instant;

/** 답을 낸 사용자와 시각. 사용 지표(#182)에 쓰며 답 내용은 담지 않는다. */
public record AttemptTime(Long userId, Instant submittedAt) {
}
