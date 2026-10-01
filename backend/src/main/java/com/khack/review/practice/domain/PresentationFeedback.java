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
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 제시 1회의 단계적 피드백 기록 (스펙 §7 5·6단계). 보여 준 힌트·설명 본문, 오늘 다시 묻기 편성 여부, 풀이 경로, 틀린 횟수를 남긴다.
 * 경로는 표시용이며 등급 계산에 쓰지 않는다. 노출 시각은 {@link AidExposure}가 가진다.
 */
@Entity
@Table(name = "presentation_feedback")
public class PresentationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long presentationId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long memoryItemId;

    @Column(length = 2_000)
    private String hintText;

    @Column(length = 10_000)
    private String explanationText;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "presentation_feedback_evidence_turn", joinColumns = @JoinColumn(name = "feedback_id"))
    @Column(name = "turn_index", nullable = false)
    @OrderBy
    private List<Integer> explanationEvidenceTurns = new ArrayList<>();

    /** 이 제시를 오늘 큐 끝에 다시 넣었는가(첫 학습의 확인 문제, 매일 학습의 다시 묻기, 변형 문제 포함). 한 번만 한다. */
    @Column(nullable = false)
    private boolean relearnQueued;

    @Enumerated(EnumType.STRING)
    private FeedbackPath path;

    /** 이 제시에서 틀린 시도 수. 반복 어려움 판단에 쓴다. */
    @Column(nullable = false)
    private int wrongAttempts;

    @Enumerated(EnumType.STRING)
    private FeedbackAction lastAction;

    private String prerequisiteConcept;

    @Column(length = 1_000)
    private String prerequisiteReason;

    protected PresentationFeedback() {
    }

    public PresentationFeedback(Long presentationId, Long userId, Long memoryItemId) {
        this.presentationId = presentationId;
        this.userId = userId;
        this.memoryItemId = memoryItemId;
    }

    public void hintShown(String text) {
        hintText = text;
    }

    public void explanationShown(String text, List<Integer> evidenceTurns) {
        explanationText = text;
        explanationEvidenceTurns = new ArrayList<>(evidenceTurns);
    }

    public void relearnQueued() {
        relearnQueued = true;
    }

    public void observe(@Nullable FeedbackPath path, int wrongAttempts, FeedbackAction action) {
        this.path = path;
        this.wrongAttempts = wrongAttempts;
        this.lastAction = action;
    }

    public void suggestPrerequisite(String concept, String reason) {
        prerequisiteConcept = concept;
        prerequisiteReason = reason;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public Long getMemoryItemId() {
        return memoryItemId;
    }

    public String getHintText() {
        return hintText;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public List<Integer> getExplanationEvidenceTurns() {
        return List.copyOf(explanationEvidenceTurns);
    }

    public boolean isRelearnQueued() {
        return relearnQueued;
    }

    public FeedbackPath getPath() {
        return path;
    }

    public int getWrongAttempts() {
        return wrongAttempts;
    }

    public FeedbackAction getLastAction() {
        return lastAction;
    }

    public String getPrerequisiteConcept() {
        return prerequisiteConcept;
    }

    public String getPrerequisiteReason() {
        return prerequisiteReason;
    }
}
