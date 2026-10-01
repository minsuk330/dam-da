package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 학습 세션 목록의 한 줄. {@code conversationId}는 원본 대화의 외부 ID(`/api/conversations/{id}`)다.
 * {@code field}는 학습 분야 라벨이며 자동 판정 전이면 null이다(스펙 §7.10).
 */
public record LearningSessionSummary(Long id, String conversationId, LearningSessionStatus status, @Nullable String topicHint, String inputPath,
        String fidelity, Instant createdAt, int unitCount, int itemCount,
        @Nullable FieldLabel field) {
}
