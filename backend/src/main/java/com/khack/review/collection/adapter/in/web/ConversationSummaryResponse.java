package com.khack.review.collection.adapter.in.web;

import com.khack.review.collection.domain.Fidelity;
import com.khack.review.collection.domain.InputPath;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.ShareSource;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 수신 대화 목록의 한 줄. {@code learningSessionId}는 이 대화로 만든 학습 세션이고, 아직 없으면 null이다. */
record ConversationSummaryResponse(
        String id,
        @Nullable Long learningSessionId,
        Instant receivedAt,
        InputPath inputPath,
        @Nullable ShareSource shareSource,
        Fidelity fidelity,
        @Nullable String topicHint,
        int userTurnCount,
        int reviewUnitCount,
        int warningCount) {

    static ConversationSummaryResponse from(SavedSession session, @Nullable Long learningSessionId) {
        return new ConversationSummaryResponse(session.id(), learningSessionId, Instant.parse(session.receivedAt()),
                InputPath.valueOf(session.source()),
                session.shareSource() == null ? null : ShareSource.valueOf(session.shareSource()),
                Fidelity.valueOf(session.transcription()), session.topicHint(),
                session.userTurns().size(), session.reviewUnits().size(), session.warnings().size());
    }
}
