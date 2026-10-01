package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.analysis.domain.MemoryItemStatus;
import com.khack.review.collection.application.ConversationQueryService;
import com.khack.review.collection.domain.UserTurn;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 컨텍스트가 학습 세션의 기억 항목을 ID로 조회하는 곳. */
@Service
public class SessionItemsQuery {

    private final LearningSessionRepository sessions;
    private final ConversationQueryService conversations;

    public SessionItemsQuery(LearningSessionRepository sessions, ConversationQueryService conversations) {
        this.sessions = sessions;
        this.conversations = conversations;
    }

    /** 확인된 기억 항목 1개와 그 출처 발화(사용자가 확인한 내용). */
    public record ConfirmedItem(Long memoryItemId, List<UserTurn> sourceTurns) {
    }

    /** {@code conversationAt}은 대화를 받은 시각이다. 서버는 실제 대화 시각을 모르므로 이것을 대화 시각으로 쓴다. */
    public record ConfirmedItems(Long sessionId, Long userId, Instant conversationAt, List<ConfirmedItem> items) {
    }

    /** 사용자의 확인된 세션에 속한 제외되지 않은 기억 항목 1개. 매일 학습 큐가 단위·종류로 후보를 가른다. */
    public record ActiveItem(Long memoryItemId, Long unitId, Long sessionId, MemoryItemKind kind) {
    }

    /**
     * 사용자의 모든 세션 중 확인을 마친 세션의 기억 항목(제외된 항목·복습 단위 제외). 매일 학습 큐의 후보 원천이다(스펙 §6.4.4).
     */
    @Transactional(readOnly = true)
    public List<ActiveItem> activeItemsOf(Long userId) {
        return sessions.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .filter(LearningSession::isConfirmed)
                .flatMap(session -> session.getUnits().stream()
                        .filter(unit -> !unit.isExcluded())
                        .flatMap(unit -> unit.getItems().stream()
                                .filter(item -> item.getStatus() != MemoryItemStatus.EXCLUDED)
                                .map(item -> new ActiveItem(item.getId(), unit.getId(), session.getId(), item.getKind()))))
                .toList();
    }

    /** 사용자의 세션이면 제외되지 않은 기억 항목 ID, 아니면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<List<Long>> activeItemIds(Long userId, Long sessionId) {
        return sessions.findById(sessionId)
                .filter(session -> Objects.equals(session.getUserId(), userId))
                .map(session -> session.items().stream()
                        .filter(item -> item.getStatus() != MemoryItemStatus.EXCLUDED)
                        .map(MemoryItem::getId)
                        .toList());
    }

    /**
     * 사용자가 확인을 마친 세션의 제외되지 않은 기억 항목과 출처 발화. 초기 평가(스펙 §6.4.2)는 확인된 내용으로만 하므로,
     * 확인 전이면 {@link IllegalStateException}이다(규칙 18).
     */
    @Transactional(readOnly = true)
    public ConfirmedItems confirmedItems(Long sessionId) {
        LearningSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션 없음: " + sessionId));
        if (!session.isConfirmed()) {
            throw new IllegalStateException("학습 세션 %d은(는) 아직 사용자 확인 전(%s)입니다.".formatted(sessionId, session.getStatus()));
        }
        Map<Integer, UserTurn> turns = conversations.evidence(session.getConversationId()).turns().stream()
                .collect(Collectors.toMap(UserTurn::index, Function.identity()));
        List<ConfirmedItem> items = session.items().stream()
                .filter(item -> item.getStatus() != MemoryItemStatus.EXCLUDED)
                .map(item -> new ConfirmedItem(item.getId(),
                        item.getSourceTurns().stream().map(turns::get).filter(Objects::nonNull).toList()))
                .toList();
        return new ConfirmedItems(sessionId, session.getUserId(), session.getCreatedAt(), items);
    }
}
