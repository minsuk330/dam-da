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

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ReviewUnit> units = new ArrayList<>();

    protected LearningSession() {
    }

    /**
     * 검증을 통과한 커넥터 입력으로 세션을 만든다. 핵심 사실과 헷갈린 지점은 각각 기억 항목 1개가 된다.
     * `meta` 발화는 출처에서 빼고(규칙 15), 출처가 남지 않은 항목과 항목이 없는 복습 단위는 만들지 않는다(규칙 2).
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
                if (!turns.isEmpty()) {
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

    public List<ReviewUnit> getUnits() {
        return List.copyOf(units);
    }
}
