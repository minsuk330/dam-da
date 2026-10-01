package com.khack.review.collection.domain;

import java.util.List;

/**
 * A stored connector call. {@code assistantSummary} and {@code totalUserTurns} only exist on sessions
 * saved with the v1 schema and are null for new ones.
 */
public record SavedSession(
        String id,
        String receivedAt,
        String source,
        String transcription,
        List<UserTurn> userTurns,
        List<ReviewUnit> reviewUnits,
        String topicHint,
        List<String> warnings,
        String assistantSummary,
        Integer totalUserTurns) {
}
