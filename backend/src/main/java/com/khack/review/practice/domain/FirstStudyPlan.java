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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 학습 세션의 학습 목표와 첫 학습 문제 계획 (스펙 §7 3·4단계, §7.8). 세션은 ID로만 참조한다.
 * 문제를 만들기 전(세션이 확인 완료 상태)에는 목표를 다시 골라 계획을 바꿀 수 있다.
 * 목표(최대 3개)와 문제(상한 12개)는 작아서 계획과 함께 읽는다.
 */
@Entity
@Table(name = "first_study_plan")
public class FirstStudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private Long sessionId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "first_study_plan_goal", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "goal_order")
    @Enumerated(EnumType.STRING)
    @Column(name = "goal", nullable = false)
    private List<LearningGoal> goals = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "first_study_plan_question", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderBy("position")
    private List<PlannedQuestionEntry> questions = new ArrayList<>();

    @Column(nullable = false)
    private Instant updatedAt;

    /** 문제 생성 상태. 이 필드를 넣기 전에 만든 계획이면 null이다. */
    @Enumerated(EnumType.STRING)
    private GenerationStatus generationStatus;

    @Column(length = 2_000)
    private String generationFailure;

    /** 목표를 고를 때마다 오른다. 늦게 끝난 옛 생성이 새 계획의 상태를 덮어쓰지 않게 한다. */
    @Column(nullable = false)
    private int generation;

    protected FirstStudyPlan() {
    }

    public FirstStudyPlan(Long userId, Long sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public void replace(List<LearningGoal> goals, List<FirstStudyComposer.PlannedQuestion> planned, Instant at) {
        this.goals.clear();
        this.goals.addAll(goals);
        this.questions.clear();
        IntStream.range(0, planned.size()).forEach(i -> this.questions.add(new PlannedQuestionEntry(i, planned.get(i))));
        this.updatedAt = at;
        this.generationStatus = GenerationStatus.GENERATING;
        this.generationFailure = null;
        this.generation++;
    }

    /** {@code generation}회차 생성이 끝났다. 그 사이 목표를 다시 골랐으면 아무것도 하지 않는다. */
    public boolean finishGeneration(int generation, int approved, String failureIfNone) {
        if (generation != this.generation) {
            return false;
        }
        generationStatus = approved > 0 ? GenerationStatus.READY : GenerationStatus.FAILED;
        generationFailure = approved > 0 ? null : truncate(failureIfNone);
        return true;
    }

    public boolean failGeneration(int generation, String failure) {
        return finishGeneration(generation, 0, failure);
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 2_000 ? text : text.substring(0, 2_000);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public List<LearningGoal> getGoals() {
        return List.copyOf(goals);
    }

    public List<PlannedQuestionEntry> getQuestions() {
        return List.copyOf(questions);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public GenerationStatus getGenerationStatus() {
        return generationStatus;
    }

    public String getGenerationFailure() {
        return generationFailure;
    }

    public int getGeneration() {
        return generation;
    }
}
