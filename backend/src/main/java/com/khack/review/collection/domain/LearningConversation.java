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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

    /** 수신 당시 그대로다. 사용자가 발화를 끼워 넣어도 이 안의 발화 index는 바꾸지 않는다. */
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

    /**
     * 사용자가 확인 화면에서 발화를 고친다(스펙 §7.3 대응 3). 원문(`verbatim`) 발화의 text는 바꿀 수 없다(규칙 14).
     */
    public void editTurn(int index, String text, Intent intent, @Nullable AiVerdict aiVerdict, @Nullable String correction) {
        ConversationTurn turn = turn(index);
        requireText(text);
        if (fidelity == Fidelity.verbatim && !text.equals(turn.getText())) {
            throw new IllegalStateException("%d번째 메시지는 원문 그대로 저장돼 있어 고칠 수 없어요.".formatted(index));
        }
        turn.edit(text, requireIntent(intent), aiVerdict, correction);
    }

    /**
     * 모델이 빠뜨린 발화를 {@code afterIndex} 뒤에 넣는다(0이면 맨 앞). 뒤 발화의 index는 1씩 밀린다.
     * 모델이 옮겨 적은 대화(`model_transcribed`)만 해당한다. 넣은 발화의 index를 돌려준다.
     */
    public int insertTurnAfter(int afterIndex, String text, Intent intent, @Nullable AiVerdict aiVerdict,
            @Nullable String correction) {
        if (fidelity != Fidelity.model_transcribed) {
            throw new IllegalStateException("원문 그대로 저장된 대화에는 메시지를 추가할 수 없어요.");
        }
        if (afterIndex != 0) {
            turn(afterIndex);
        }
        requireText(text);
        int index = afterIndex + 1;
        turns.stream().filter(turn -> turn.getTurnIndex() >= index).forEach(turn -> turn.moveTo(turn.getTurnIndex() + 1));
        turns.add(ConversationTurn.from(new UserTurn(index, text, null, requireIntent(intent), aiVerdict, correction)));
        turns.sort(Comparator.comparingInt(ConversationTurn::getTurnIndex));
        return index;
    }

    private ConversationTurn turn(int index) {
        return turns.stream().filter(turn -> turn.getTurnIndex() == index).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("%d번째 메시지가 없어요.".formatted(index)));
    }

    private static void requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("메시지 내용을 입력해 주세요.");
        }
    }

    private static Intent requireIntent(Intent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("메시지 종류를 골라 주세요.");
        }
        return intent;
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
