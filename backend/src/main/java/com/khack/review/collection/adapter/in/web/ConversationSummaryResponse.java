package com.khack.review.collection.adapter.in.web;

import com.khack.review.collection.domain.Fidelity;
import com.khack.review.collection.domain.InputPath;
import com.khack.review.collection.domain.SavedSession;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** 수신 대화 목록의 한 줄. */
record ConversationSummaryResponse(
        String id,
        Instant receivedAt,
        InputPath inputPath,
        Fidelity fidelity,
        @Nullable String topicHint,
        int userTurnCount,
        int reviewUnitCount,
        int warningCount) {

    static ConversationSummaryResponse from(SavedSession session) {
        return new ConversationSummaryResponse(session.id(), Instant.parse(session.receivedAt()),
                InputPath.valueOf(session.source()), Fidelity.valueOf(session.transcription()), session.topicHint(),
                session.userTurns().size(), session.reviewUnits().size(), session.warnings().size());
    }
}
