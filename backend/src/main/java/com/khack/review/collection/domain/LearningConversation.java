package com.khack.review.collection.domain;

import com.khack.review.common.json.Json;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 수신한 학습 대화 1건 (스펙 §8.3 "학습 대화"). 입력 경로와 원문 여부를 항상 가진다(규칙 14).
 * 복습 단위는 분석 컨텍스트가 엔티티로 옮기기 전까지 커넥터가 보낸 모양 그대로 보관한다.
 */
@Entity
@Table(name = "learning_conversation")
public class LearningConversation {

    static final int LONG_TEXT = 100_000;
    public static final int MAX_RAW_TRANSCRIPT = 1_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부(커넥터 응답, 도구, 벤치마크)에 보이는 ID. */
    @Column(nullable = false, unique = true, updatable = false)
    private String sessionId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InputPath inputPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Fidelity fidelity;

    @Column(nullable = false)
    private Instant receivedAt;

    private String topicHint;

    @ElementCollection
    @CollectionTable(name = "conversation_turn", joinColumns = @JoinColumn(name = "conversation_id"))
    @OrderBy("turnIndex")
    private List<ConversationTurn> turns = new ArrayList<>();

    @Convert(converter = JsonListConverters.ReviewUnits.class)
    @Column(nullable = false, length = 1_000_000)
    private List<ReviewUnit> reviewUnits = new ArrayList<>();

    @Convert(converter = JsonListConverters.Strings.class)
    @Column(nullable = false, length = LONG_TEXT)
    private List<String> warnings = new ArrayList<>();

    /** 공유 링크·붙여넣기로 받은 원문 대화(JSON). 커넥터 입력은 원문이 없어 null이다. */
    @Column(length = MAX_RAW_TRANSCRIPT)
    private String rawTranscript;

    protected LearningConversation() {
    }

    /** 커넥터로 받은 대화. 발화는 모델이 옮겨 적은 것이다(규칙 13). */
    public static LearningConversation fromConnector(Long userId, SessionInput input, List<String> warnings, Instant receivedAt) {
        LearningConversation conversation = new LearningConversation();
        conversation.sessionId = UUID.randomUUID().toString();
        conversation.userId = userId;
        conversation.inputPath = InputPath.connector;
        conversation.fidelity = Fidelity.model_transcribed;
        conversation.receivedAt = receivedAt;
        conversation.topicHint = input.topicHint();
        input.userTurns().forEach(turn -> conversation.turns.add(ConversationTurn.from(turn)));
        conversation.reviewUnits = new ArrayList<>(input.reviewUnits());
        conversation.warnings = new ArrayList<>(warnings);
        return conversation;
    }

    /** 공유 링크·붙여넣기로 받은 대화. 발화는 원문과 맞춘 것이다(규칙 14). */
    public static LearningConversation fromTranscript(Long userId, RawConversation raw, SessionInput input, List<String> warnings,
            Instant receivedAt) {
        LearningConversation conversation = new LearningConversation();
        conversation.sessionId = UUID.randomUUID().toString();
        conversation.userId = userId;
        conversation.inputPath = raw.inputPath();
        conversation.fidelity = Fidelity.verbatim;
        conversation.receivedAt = receivedAt;
        conversation.topicHint = input.topicHint() != null ? input.topicHint() : raw.title();
        input.userTurns().forEach(turn -> conversation.turns.add(ConversationTurn.from(turn)));
        conversation.reviewUnits = new ArrayList<>(input.reviewUnits());
        conversation.warnings = new ArrayList<>(warnings);
        conversation.rawTranscript = Json.MAPPER.writeValueAsString(raw);
        return conversation;
    }

    /** 도구·뷰어·벤치마크가 쓰는 조회 형태. */
    public SavedSession toSavedSession() {
        return new SavedSession(sessionId, receivedAt.toString(), inputPath.name(), fidelity.name(),
                turns.stream().map(ConversationTurn::toUserTurn).toList(),
                List.copyOf(reviewUnits), topicHint, List.copyOf(warnings), null, null);
    }

    public List<UserTurn> userTurns() {
        return turns.stream().map(ConversationTurn::toUserTurn).toList();
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public InputPath getInputPath() {
        return inputPath;
    }

    public Fidelity getFidelity() {
        return fidelity;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getRawTranscript() {
        return rawTranscript;
    }
}
