package com.khack.review.question.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 문제 1개 (스펙 §8.3 "문제 후보", "복습 문제"). 기억 항목 1개를 대상으로 하며(§6.4.1), 같은 항목의 변형 문제는
 * 기억 상태를 공유한다(규칙 7, 17). 품질 검사를 통과해 승인된 문제만 사용자에게 보인다(규칙 3).
 */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long memoryItemId;

    /** 첫 학습 계획의 순서. 매일 학습·변형 문제는 null. */
    private Integer planPosition;

    /** 첫 학습 문제면 학습 목표 이름. */
    private String learningGoal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false, length = 10_000)
    private String stem;

    @Convert(converter = JsonLists.Strings.class)
    @Column(nullable = false, length = 10_000)
    private List<String> choices = new ArrayList<>();

    private Integer correctChoice;

    @Convert(converter = JsonLists.Strings.class)
    @Column(nullable = false, length = 10_000)
    private List<String> answerCriteria = new ArrayList<>();

    @Column(nullable = false, length = 10_000)
    private String modelAnswer;

    @Convert(converter = JsonLists.Integers.class)
    @Column(nullable = false, length = 1_000)
    private List<Integer> evidenceTurns = new ArrayList<>();

    /** 변형 문제면 원래 문제 ID. */
    private Long variantOfId;

    /** 같은 계획 위치·변형 요청 안에서 몇 번째 생성인지. */
    @Column(nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.CANDIDATE;

    /** 품질 검사 결과 요약. */
    @Column(length = 2_000)
    private String qualityNote;

    @Column(nullable = false)
    private Instant createdAt;

    protected Question() {
    }

    public record Content(QuestionType type, String stem, List<String> choices, Integer correctChoice,
            List<String> answerCriteria, String modelAnswer, List<Integer> evidenceTurns) {
    }

    public static Question candidate(Long userId, Long sessionId, Long memoryItemId, Integer planPosition, String learningGoal,
            Long variantOfId, int attempt, Content content, Instant createdAt) {
        Question question = new Question();
        question.userId = userId;
        question.sessionId = sessionId;
        question.memoryItemId = memoryItemId;
        question.planPosition = planPosition;
        question.learningGoal = learningGoal;
        question.variantOfId = variantOfId;
        question.attempt = attempt;
        question.type = content.type();
        question.stem = content.stem();
        question.choices = new ArrayList<>(content.choices());
        question.correctChoice = content.correctChoice();
        question.answerCriteria = new ArrayList<>(content.answerCriteria());
        question.modelAnswer = content.modelAnswer();
        question.evidenceTurns = new ArrayList<>(content.evidenceTurns());
        question.createdAt = createdAt;
        return question;
    }

    public void approve(String note) {
        requireStatus(QuestionStatus.CANDIDATE, QuestionStatus.APPROVED);
        status = QuestionStatus.APPROVED;
        qualityNote = truncate(note);
    }

    public void reject(String note) {
        requireStatus(QuestionStatus.CANDIDATE, QuestionStatus.APPROVED);
        status = QuestionStatus.REJECTED;
        qualityNote = truncate(note);
    }

    /** 보류 후 재검사에서 떨어진 승인 문제를 더 쓰지 않는다. */
    public void retire(String note) {
        requireStatus(QuestionStatus.APPROVED);
        status = QuestionStatus.RETIRED;
        qualityNote = truncate(note);
    }

    private void requireStatus(QuestionStatus... allowed) {
        if (!List.of(allowed).contains(status)) {
            throw new IllegalStateException("문제 %d: %s 상태에서는 할 수 없습니다.".formatted(id, status));
        }
    }

    private static String truncate(String note) {
        return note == null || note.length() <= 2_000 ? note : note.substring(0, 2_000);
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

    public Long getMemoryItemId() {
        return memoryItemId;
    }

    public Integer getPlanPosition() {
        return planPosition;
    }

    public String getLearningGoal() {
        return learningGoal;
    }

    public QuestionType getType() {
        return type;
    }

    public String getStem() {
        return stem;
    }

    public List<String> getChoices() {
        return List.copyOf(choices);
    }

    public Integer getCorrectChoice() {
        return correctChoice;
    }

    public List<String> getAnswerCriteria() {
        return List.copyOf(answerCriteria);
    }

    public String getModelAnswer() {
        return modelAnswer;
    }

    public List<Integer> getEvidenceTurns() {
        return List.copyOf(evidenceTurns);
    }

    public Long getVariantOfId() {
        return variantOfId;
    }

    public int getAttempt() {
        return attempt;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public String getQualityNote() {
        return qualityNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
