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
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 기억 항목 하나를 대상으로 생성한 문제. 같은 항목의 문제와 변형 문제는 기억 상태를 공유한다(스펙 규칙 17).
 * 학습 세션과 기억 항목은 분석 컨텍스트의 것이며 ID로만 참조한다.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningGoal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 객관식만 값이 있다. */
    @Convert(converter = JsonListConverters.Strings.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> choices;

    /** 객관식 정답의 {@code choices} 위치(0부터). 객관식이 아니면 null. */
    private @Nullable Integer correctChoiceIndex;

    /** 답변 판정(Jev)에 쓰는 필수 기준. 사용자에게 보여주지 않는다. */
    @Convert(converter = JsonListConverters.Strings.class)
    @Column(nullable = false, columnDefinition = "text")
    private List<String> answerCriteria;

    @Column(nullable = false, columnDefinition = "text")
    private String modelAnswer;

    @Column(nullable = false, columnDefinition = "text")
    private String hint;

    @Column(nullable = false, columnDefinition = "text")
    private String explanation;

    /** 원문 근거 발화 index. 비어 있을 수 없다(스펙 규칙 2·15). */
    @Convert(converter = JsonListConverters.Integers.class)
    @Column(nullable = false)
    private List<Integer> evidenceTurns;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private Instant createdAt;

    protected Question() {
    }

    private Question(Long userId, Long sessionId, Long memoryItemId, QuestionTarget target, Content content,
            String promptVersion, Instant createdAt) {
        if (target.evidenceTurns().isEmpty()) {
            throw new IllegalArgumentException("문제는 원문 근거 발화를 가져야 합니다.");
        }
        this.userId = userId;
        this.sessionId = sessionId;
        this.memoryItemId = memoryItemId;
        this.goal = target.goal();
        this.type = target.type();
        this.status = QuestionStatus.CANDIDATE;
        this.body = content.body();
        this.choices = List.copyOf(content.choices());
        this.correctChoiceIndex = content.correctChoiceIndex();
        this.answerCriteria = List.copyOf(content.answerCriteria());
        this.modelAnswer = content.modelAnswer();
        this.hint = content.hint();
        this.explanation = content.explanation();
        this.evidenceTurns = List.copyOf(target.evidenceTurns());
        this.promptVersion = promptVersion;
        this.createdAt = createdAt;
    }

    /** 품질 검사 전의 문제 후보를 만든다. */
    public static Question candidate(Long userId, Long sessionId, Long memoryItemId, QuestionTarget target,
            Content content, String promptVersion, Instant createdAt) {
        return new Question(userId, sessionId, memoryItemId, target, content, promptVersion, createdAt);
    }

    /** LLM이 생성한 문제 내용. */
    public record Content(
            String body,
            List<String> choices,
            @Nullable Integer correctChoiceIndex,
            List<String> answerCriteria,
            String modelAnswer,
            String hint,
            String explanation) {
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

    public LearningGoal getGoal() {
        return goal;
    }

    public QuestionType getType() {
        return type;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }

    public List<String> getChoices() {
        return choices;
    }

    public @Nullable Integer getCorrectChoiceIndex() {
        return correctChoiceIndex;
    }

    public List<String> getAnswerCriteria() {
        return answerCriteria;
    }

    public String getModelAnswer() {
        return modelAnswer;
    }

    public String getHint() {
        return hint;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<Integer> getEvidenceTurns() {
        return evidenceTurns;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
