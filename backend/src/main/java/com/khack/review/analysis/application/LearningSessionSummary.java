package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 학습 세션 목록의 한 줄. {@code conversationId}는 원본 대화의 외부 ID(`/api/conversations/{id}`)다. */
public record LearningSessionSummary(Long id, String conversationId, LearningSessionStatus status, @Nullable String topicHint, String inputPath,
        String fidelity, Instant createdAt, int unitCount, int itemCount) {
}
