package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.analysis.domain.MemoryItemStatus;
import com.khack.review.analysis.domain.ReviewUnit;
import com.khack.review.analysis.domain.ReviewUnitVerdict;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 학습 세션의 학습 대상 내용. 다른 컨텍스트(문제 생성 등)가 엔티티 대신 읽는 형태다.
 * 사용자가 뺀 복습 단위와 기억 항목은 들어가지 않는다.
 */
public record SessionContent(
        Long sessionId,
        Long userId,
        Long conversationId,
        LearningSessionStatus status,
        @Nullable String topicHint,
        List<Unit> units) {

    public record Unit(Long id, String title, ReviewUnitVerdict verdict, List<Item> items) {
    }

    /** {@code content}는 핵심 사실이면 그 내용, 헷갈린 지점이면 사용자가 믿었던 내용이다. */
    public record Item(Long id, MemoryItemKind kind, String content, List<Integer> sourceTurns) {
    }

    public static SessionContent of(LearningSession session) {
        List<Unit> units = session.getUnits().stream()
                .filter(unit -> !unit.isExcluded())
                .map(SessionContent::unit)
                .filter(unit -> !unit.items().isEmpty())
                .toList();
        return new SessionContent(session.getId(), session.getUserId(), session.getConversationId(), session.getStatus(),
                session.getTopicHint(), units);
    }

    private static Unit unit(ReviewUnit unit) {
        List<Item> items = unit.getItems().stream()
                .filter(item -> item.getStatus() != MemoryItemStatus.EXCLUDED)
                .map(SessionContent::item)
                .toList();
        return new Unit(unit.getId(), unit.getTitle(), unit.getVerdict(), items);
    }

    private static Item item(MemoryItem item) {
        return new Item(item.getId(), item.getKind(), item.getContent(), item.getSourceTurns());
    }
}
