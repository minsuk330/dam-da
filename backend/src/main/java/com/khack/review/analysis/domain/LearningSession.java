package com.khack.review.analysis.domain;

import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 학습 세션: 학습 대화 1개에서 만든 학습 단위 묶음 (스펙 §7 2단계). 상태는 {@link LearningSessionStatus}의
 * 순서로만 넘어간다. 학습 대화는 ID로만 참조한다.
 */
@Entity
@Table(name = "learning_session")
public class LearningSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long conversationId;

    private String topicHint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningSessionStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    /** 사용자가 확인을 마친 시각. 확인 전에는 null이다. */
    private Instant confirmedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ReviewUnit> units = new ArrayList<>();

    protected LearningSession() {
    }

    /**
     * 검증을 통과한 커넥터 입력으로 세션을 만든다. 핵심 사실과 헷갈린 지점은 각각 기억 항목 1개가 된다.
     * `meta` 발화는 출처에서 빼고(규칙 15), 출처가 남지 않은 항목과 항목이 없는 복습 단위는 만들지 않는다(규칙 2).
     * `userBelief`가 빈 헷갈린 지점은 건너뛴다. 검증기가 경고로 남기므로 대화 저장은 실패시키지 않는다.
     */
    public static LearningSession create(Long userId, Long conversationId, SessionInput input, Instant createdAt) {
        LearningSession session = new LearningSession();
        session.userId = userId;
        session.conversationId = conversationId;
        session.topicHint = input.topicHint();
        session.status = LearningSessionStatus.RECEIVED;
        session.createdAt = createdAt;

        Set<Integer> metaTurns = input.userTurns().stream()
                .filter(turn -> turn.intent() == Intent.meta)
                .map(UserTurn::index)
                .collect(Collectors.toSet());
        for (com.khack.review.collection.domain.ReviewUnit source : input.reviewUnits()) {
            ReviewUnit unit = new ReviewUnit(session, session.units.size(), source.title());
            for (KeyPoint point : source.keyPoints() == null ? List.<KeyPoint>of() : source.keyPoints()) {
                List<Integer> turns = withoutMeta(point.turns(), metaTurns);
                if (!turns.isEmpty()) {
                    unit.addItem(kindOf(point.effectiveKind()), point.point(), turns);
                }
            }
            for (ConfusionPoint confusion : source.confusions()) {
                List<Integer> turns = withoutMeta(List.of(confusion.turn()), metaTurns);
                boolean hasBelief = confusion.userBelief() != null && !confusion.userBelief().isBlank();
                if (hasBelief && !turns.isEmpty()) {
                    unit.addItem(MemoryItemKind.CONFUSION, confusion.userBelief(), turns);
                }
            }
            if (!unit.getItems().isEmpty()) {
                session.units.add(unit);
            }
        }
        return session;
    }

    private static List<Integer> withoutMeta(List<Integer> turns, Set<Integer> metaTurns) {
        return turns == null ? List.of() : turns.stream().filter(turn -> !metaTurns.contains(turn)).distinct().sorted().toList();
    }

    private static MemoryItemKind kindOf(FactKind kind) {
        return switch (kind) {
            case fact -> MemoryItemKind.FACT;
            case warning -> MemoryItemKind.WARNING;
            case practice -> MemoryItemKind.PRACTICE;
        };
    }

    /** 정해진 다음 상태로만 넘어간다. 아니면 {@link IllegalStateException}. */
    public void moveTo(LearningSessionStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("학습 세션 %d: %s에서 %s로 넘어갈 수 없습니다.".formatted(id, status, next));
        }
        status = next;
    }

    /** 확인 대기 중에만 사용자가 내용을 고칠 수 있다(규칙 9, 12). */
    public void requireEditable() {
        if (status != LearningSessionStatus.AWAITING_CONFIRMATION) {
            throw new IllegalStateException("학습 세션 %d은(는) %s 상태라 고칠 수 없습니다. 확인 대기 중에만 고칠 수 있습니다."
                    .formatted(id, status));
        }
    }

    public void setUnitExcluded(Long unitId, boolean excluded) {
        requireEditable();
        ReviewUnit unit = unit(unitId);
        if (excluded) {
            unit.exclude();
        } else {
            unit.include();
        }
    }

    public void setItemExcluded(Long itemId, boolean excluded) {
        requireEditable();
        MemoryItem item = item(itemId);
        if (excluded) {
            item.exclude();
        } else if (item.getUnit().isExcluded()) {
            throw new IllegalStateException("기억 항목 %d이 속한 복습 단위가 제외되어 있습니다. 복습 단위를 먼저 다시 넣으세요.".formatted(itemId));
        } else {
            item.include();
        }
    }

    /** 발화가 `meta`로 바뀌면 출처에서 뺀다(규칙 15). */
    public void turnBecameMeta(int turn) {
        requireEditable();
        items().forEach(item -> item.removeSourceTurn(turn));
    }

    /**
     * 빠진 발화가 {@code turn} 자리에 들어가 뒤 발화의 index가 1씩 밀렸다. 출처 index를 따라 옮기고,
     * {@code sourceOf}에 든 기억 항목의 출처에 새 발화를 더한다.
     */
    public void turnInserted(int turn, List<Long> sourceOf) {
        requireEditable();
        items().forEach(item -> item.shiftSourceTurnsFrom(turn));
        sourceOf.forEach(itemId -> item(itemId).addSourceTurn(turn));
    }

    /** 사용자가 확인을 마쳤다. 이후 내용은 고칠 수 없고 문제 생성이 열린다(규칙 12). */
    public void confirm(Instant at) {
        requireEditable();
        moveTo(LearningSessionStatus.CONFIRMED);
        confirmedAt = at;
    }

    public boolean isConfirmed() {
        return status.compareTo(LearningSessionStatus.CONFIRMED) >= 0;
    }

    private ReviewUnit unit(Long unitId) {
        return units.stream().filter(unit -> unit.getId().equals(unitId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("학습 세션 %d에 복습 단위 %d이 없습니다.".formatted(id, unitId)));
    }

    private MemoryItem item(Long itemId) {
        return items().stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("학습 세션 %d에 기억 항목 %d이 없습니다.".formatted(id, itemId)));
    }

    public List<MemoryItem> items() {
        return units.stream().flatMap(unit -> unit.getItems().stream()).toList();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getTopicHint() {
        return topicHint;
    }

    public LearningSessionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public List<ReviewUnit> getUnits() {
        return List.copyOf(units);
    }
}
