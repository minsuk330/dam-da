package com.khack.review.practice.domain;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 풀이 세션 1회 (스펙 §9.4 복습 세션). 낼 문제를 순서대로 담고, 제시는 {@link QuestionPresentation}으로 따로 남긴다.
 * 같은 날 재확인(§6.4.8)은 큐 끝에 덧붙인다.
 */
@Entity
@Table(name = "practice_session")
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PracticeKind kind;

    /** 첫 학습이면 그 학습 세션. 매일 학습은 여러 세션을 섞으므로 null. */
    private Long learningSessionId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "practice_queue", joinColumns = @JoinColumn(name = "practice_session_id"))
    @OrderBy("position")
    private List<PracticeQueueEntry> queue = new ArrayList<>();

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    protected PracticeSession() {
    }

    public static PracticeSession firstStudy(Long userId, Long learningSessionId, List<Long> questionIds, Instant at) {
        PracticeSession session = new PracticeSession();
        session.userId = userId;
        session.kind = PracticeKind.FIRST_STUDY;
        session.learningSessionId = learningSessionId;
        questionIds.forEach(id -> session.enqueue(id, null));
        session.startedAt = at;
        return session;
    }

    /** 같은 날 재확인을 큐 끝에 넣는다. 끝난 세션이면 다시 연다. */
    public void enqueueRecheck(Long questionId, Long recheckOfPresentationId) {
        enqueue(questionId, recheckOfPresentationId);
        completedAt = null;
    }

    /** 이 제시를 다시 묻는 항목이 이미 큐에 있는가. 오늘 다시 묻기는 제시마다 한 번만 하므로 편성 전에 확인한다. */
    public boolean hasRecheckOf(Long presentationId) {
        return queue.stream().anyMatch(entry -> presentationId.equals(entry.getRecheckOfPresentationId()));
    }

    /** 오늘 다시 묻기로 큐에 넣은 항목 수. 하루 분량 상한 계산에 쓴다. */
    public int recheckCount() {
        return (int) queue.stream().filter(entry -> entry.getRecheckOfPresentationId() != null).count();
    }

    private void enqueue(Long questionId, @Nullable Long recheckOf) {
        queue.add(new PracticeQueueEntry(queue.size(), questionId, recheckOf));
    }

    public void complete(Instant at) {
        if (completedAt == null) {
            completedAt = at;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public PracticeKind getKind() {
        return kind;
    }

    public Long getLearningSessionId() {
        return learningSessionId;
    }

    public List<PracticeQueueEntry> getQueue() {
        return List.copyOf(queue);
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
