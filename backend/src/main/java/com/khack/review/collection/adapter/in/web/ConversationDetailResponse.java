package com.khack.review.collection.adapter.in.web;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Fidelity;
import com.khack.review.collection.domain.InputPath;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.UserTurn;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 수신 대화 1건. {@code fidelity}가 {@code model_transcribed}이면 발화는 원문이 아니다(규칙 13).
 * {@code aiVerdict}는 대화 중 AI가 한 판정을 추출한 값이고, 사용자 답변 판정이 아니다.
 */
record ConversationDetailResponse(
        String id,
        Instant receivedAt,
        InputPath inputPath,
        Fidelity fidelity,
        @Nullable String topicHint,
        List<UserTurnResponse> userTurns,
        List<ReviewUnitResponse> reviewUnits,
        List<String> warnings) {

    static ConversationDetailResponse from(SavedSession session) {
        return new ConversationDetailResponse(session.id(), Instant.parse(session.receivedAt()),
                InputPath.valueOf(session.source()), Fidelity.valueOf(session.transcription()), session.topicHint(),
                session.userTurns().stream().map(UserTurnResponse::from).toList(),
                session.reviewUnits().stream().map(ReviewUnitResponse::from).toList(),
                session.warnings());
    }

    record UserTurnResponse(
            int index,
            String text,
            @Nullable String quotedText,
            Intent intent,
            AiVerdict aiVerdict,
            @Nullable String correction) {

        static UserTurnResponse from(UserTurn turn) {
            return new UserTurnResponse(turn.index(), turn.text(), turn.quotedText(), turn.intent(),
                    turn.effectiveVerdict(), turn.correction());
        }
    }

    /** {@code evidenceTurns}는 핵심 내용과 헷갈린 지점이 가리키는 발화 번호의 합집합이다. */
    record ReviewUnitResponse(
            String title,
            List<KeyPointResponse> keyPoints,
            List<ConfusionPointResponse> confusionPoints,
            List<Integer> evidenceTurns) {

        static ReviewUnitResponse from(ReviewUnit unit) {
            List<KeyPoint> keyPoints = unit.keyPoints() == null ? List.of() : unit.keyPoints();
            return new ReviewUnitResponse(unit.title(),
                    keyPoints.stream().map(KeyPointResponse::from).toList(),
                    unit.confusions().stream().map(ConfusionPointResponse::from).toList(),
                    unit.evidenceTurns());
        }
    }

    record KeyPointResponse(String point, List<Integer> turns, FactKind kind) {

        static KeyPointResponse from(KeyPoint point) {
            return new KeyPointResponse(point.point(), point.turns() == null ? List.of() : point.turns(),
                    point.effectiveKind());
        }
    }

    record ConfusionPointResponse(int turn, String userBelief) {

        static ConfusionPointResponse from(ConfusionPoint confusion) {
            return new ConfusionPointResponse(confusion.turn(), confusion.userBelief());
        }
    }
}
