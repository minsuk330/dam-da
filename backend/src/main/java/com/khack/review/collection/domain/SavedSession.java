package com.khack.review.collection.domain;

import java.util.List;

/**
 * 저장된 학습 대화의 조회 형태. 개발 뷰어, `/dev/sessions.json`, tools CLI와 벤치마크 채점기가 쓴다.
 * {@code source}는 입력 경로, {@code transcription}은 원문 여부다. {@code assistantSummary}와
 * {@code totalUserTurns}는 v1 스키마 시절 값이라 새로 저장한 대화에서는 항상 null이다.
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
