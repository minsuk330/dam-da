package com.khack.review.memory.domain;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.annotations.ColumnDefault;

/**
 * 기억 항목 1개의 FSRS 기억 상태 (스펙 §6.4). 첫 등급을 입력할 때 만든다. 그 전의 항목은 "아직 확인 전"이다.
 * 매일 학습 큐가 다음 복습 시각으로 조회할 수 있도록 java-fsrs {@link Card}를 컬럼으로 펼쳐 저장한다.
 */
@Entity
@Table(name = "memory_state")
public class MemoryState {

    /** 이 횟수만큼 연속 보류되면 자동 출제에서 뺀다. */
    public static final int MAX_CONSECUTIVE_HOLDS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long memoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    private Integer step;

    private Double stability;

    private Double difficulty;

    /** 다음 복습 시각. */
    @Column(nullable = false)
    private Instant due;

    private Instant lastReview;

    /** 목표 유지율 (스펙 §6.4.7). 기억 강도로 정한다. */
    @Column(nullable = false)
    private double desiredRetention;

    /** 마지막 갱신에 쓴 매개변수 버전. */
    @Column(nullable = false)
    private int parametersVersion;

    /** 연속으로 보류된 횟수. 등급을 받으면 0이 된다. DB 기본값은 이 컬럼이 생기기 전 행을 위한 것이다. */
    @Column(nullable = false)
    @ColumnDefault("0")
    private int consecutiveHolds;

    /** 연속 보류로 자동 출제에서 뺐다. 사용자 확인 대상이다(스펙 §6.4.5 보류 처리). */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean autoQuestionsPaused;

    protected MemoryState() {
    }

    public MemoryState(Long userId, Long memoryItemId, double desiredRetention, Instant now) {
        this.userId = userId;
        this.memoryItemId = memoryItemId;
        this.desiredRetention = desiredRetention;
        apply(Card.builder().cardId(Math.toIntExact(memoryItemId)).due(now).build(), 0);
    }

    /** 등급 하나를 반영한다. 같은 상태를 주면 같은 결과가 나온다(간격 흔들기 없음). */
    public void review(Scheduler scheduler, int parametersVersion, Rating rating, Instant reviewedAt) {
        apply(scheduler.reviewCard(toCard(), rating, reviewedAt).card(), parametersVersion);
        consecutiveHolds = 0;
    }

    /**
     * 보류를 기록한다. FSRS 상태는 바꾸지 않고 다음 매일 학습 큐 후보로 남는다.
     * 연속 {@value #MAX_CONSECUTIVE_HOLDS}회가 되면 자동 출제에서 빼고 사용자 확인 대상으로 표시한다.
     */
    public void hold() {
        consecutiveHolds++;
        if (consecutiveHolds >= MAX_CONSECUTIVE_HOLDS) {
            autoQuestionsPaused = true;
        }
    }

    /** 지금 떠올릴 수 있는 확률 R. 아직 복습한 적이 없으면 비어 있다. */
    public Optional<Double> retrievability(Scheduler scheduler, Instant at) {
        return lastReview == null ? Optional.empty() : Optional.of(scheduler.getCardRetrievability(toCard(), at));
    }

    public void changeDesiredRetention(double desiredRetention) {
        this.desiredRetention = desiredRetention;
    }

    private Card toCard() {
        return Card.builder()
                .cardId(Math.toIntExact(memoryItemId))
                .state(state)
                .step(step)
                .stability(stability)
                .difficulty(difficulty)
                .due(due)
                .lastReview(lastReview)
                .build();
    }

    private void apply(Card card, int parametersVersion) {
        this.state = card.getState();
        this.step = card.getStep();
        this.stability = card.getStability();
        this.difficulty = card.getDifficulty();
        this.due = card.getDue();
        this.lastReview = card.getLastReview();
        this.parametersVersion = parametersVersion;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getMemoryItemId() {
        return memoryItemId;
    }

    public State getState() {
        return state;
    }

    public Double getStability() {
        return stability;
    }

    public Double getDifficulty() {
        return difficulty;
    }

    public Instant getDue() {
        return due;
    }

    public Instant getLastReview() {
        return lastReview;
    }

    public double getDesiredRetention() {
        return desiredRetention;
    }

    public int getConsecutiveHolds() {
        return consecutiveHolds;
    }

    public boolean isAutoQuestionsPaused() {
        return autoQuestionsPaused;
    }

    public int getParametersVersion() {
        return parametersVersion;
    }
}
