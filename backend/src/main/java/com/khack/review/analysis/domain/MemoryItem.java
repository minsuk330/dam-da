package com.khack.review.analysis.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * 기억 항목: 기억 상태의 단위 (스펙 §6.4.1, §8.3). 복습 단위의 핵심 사실 1개 또는 헷갈린 지점 1개다.
 * 기억 상태(FSRS)와 문제는 이 ID를 참조한다.
 */
@Entity
@Table(name = "memory_item")
public class MemoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_unit_id")
    private ReviewUnit unit;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryItemKind kind;

    /** 핵심 사실이면 그 내용, 헷갈린 지점이면 사용자가 믿었던 내용(`userBelief`). */
    @Column(nullable = false, length = 10_000)
    private String content;

    /** 출처 발화 index. `meta` 발화는 들어가지 않는다(규칙 15). */
    @ElementCollection
    @CollectionTable(name = "memory_item_source_turn", joinColumns = @JoinColumn(name = "memory_item_id"))
    @Column(name = "turn_index", nullable = false)
    @OrderBy
    private List<Integer> sourceTurns = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryItemStatus status = MemoryItemStatus.NEW;

    protected MemoryItem() {
    }

    MemoryItem(ReviewUnit unit, int position, MemoryItemKind kind, String content, List<Integer> sourceTurns) {
        this.unit = unit;
        this.position = position;
        this.kind = kind;
        this.content = content;
        this.sourceTurns = new ArrayList<>(sourceTurns);
    }

    public void exclude() {
        status = MemoryItemStatus.EXCLUDED;
    }

    /** 제외를 되돌린다. 출처 발화가 없으면 근거가 없어 되돌릴 수 없다(규칙 2). */
    void include() {
        if (sourceTurns.isEmpty()) {
            throw new IllegalStateException("근거 메시지가 없어 다시 넣을 수 없어요.");
        }
        status = MemoryItemStatus.NEW;
    }

    /** 발화가 끼워 들어가 {@code fromIndex} 이상 발화의 index가 1씩 밀렸다. */
    void shiftSourceTurnsFrom(int fromIndex) {
        sourceTurns.replaceAll(turn -> turn >= fromIndex ? turn + 1 : turn);
    }

    void addSourceTurn(int turn) {
        if (!sourceTurns.contains(turn)) {
            sourceTurns.add(turn);
            sourceTurns.sort(null);
        }
    }

    /** 출처에서 발화를 뺀다. 출처가 남지 않으면 근거가 없어 제외한다(규칙 2). */
    void removeSourceTurn(int turn) {
        sourceTurns.remove(Integer.valueOf(turn));
        if (sourceTurns.isEmpty()) {
            exclude();
        }
    }

    public boolean isExcluded() {
        return status == MemoryItemStatus.EXCLUDED;
    }

    public Long getId() {
        return id;
    }

    public ReviewUnit getUnit() {
        return unit;
    }

    public int getPosition() {
        return position;
    }

    public MemoryItemKind getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }

    public List<Integer> getSourceTurns() {
        return List.copyOf(sourceTurns);
    }

    public MemoryItemStatus getStatus() {
        return status;
    }
}
