package com.khack.review.analysis.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 복습 단위: 하나의 개념 또는 기술 (스펙 §8.3). 기억 상태는 소속 기억 항목별로 관리한다.
 * 커넥터 스키마의 복습 단위({@code collection.domain.ReviewUnit})에서 만든다.
 */
@Entity
@Table(name = "review_unit")
public class ReviewUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learning_session_id")
    private LearningSession session;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 1_000)
    private String title;

    @Column(nullable = false)
    private boolean excluded;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<MemoryItem> items = new ArrayList<>();

    protected ReviewUnit() {
    }

    ReviewUnit(LearningSession session, int position, String title) {
        this.session = session;
        this.position = position;
        this.title = title;
    }

    MemoryItem addItem(MemoryItemKind kind, String content, List<Integer> sourceTurns) {
        MemoryItem item = new MemoryItem(this, items.size(), kind, content, sourceTurns);
        items.add(item);
        return item;
    }

    /** 근거 발화: 소속 기억 항목 출처 발화의 합집합 (규칙 2, 15). 저장하지 않고 계산한다. */
    public List<Integer> evidenceTurns() {
        TreeSet<Integer> turns = new TreeSet<>();
        items.forEach(item -> turns.addAll(item.getSourceTurns()));
        return List.copyOf(turns);
    }

    /** 복습 단위를 빼면 소속 기억 항목도 모두 뺀다. */
    public void exclude() {
        excluded = true;
        items.forEach(MemoryItem::exclude);
    }

    public Long getId() {
        return id;
    }

    public LearningSession getSession() {
        return session;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public List<MemoryItem> getItems() {
        return List.copyOf(items);
    }
}
