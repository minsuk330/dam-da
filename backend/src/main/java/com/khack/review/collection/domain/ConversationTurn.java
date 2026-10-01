package com.khack.review.collection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 학습 대화에 저장된 사용자 발화 1개. 커넥터 스키마 v5의 {@link UserTurn}과 같은 내용이다. */
@Embeddable
public class ConversationTurn {

    @Column(name = "turn_index", nullable = false)
    private int turnIndex;

    @Column(nullable = false, length = LearningConversation.LONG_TEXT)
    private String text;

    @Column(length = LearningConversation.LONG_TEXT)
    private String quotedText;

    @Enumerated(EnumType.STRING)
    private Intent intent;

    @Enumerated(EnumType.STRING)
    private AiVerdict aiVerdict;

    @Column(length = LearningConversation.LONG_TEXT)
    private String correction;

    protected ConversationTurn() {
    }

    static ConversationTurn from(UserTurn turn) {
        ConversationTurn stored = new ConversationTurn();
        stored.turnIndex = turn.index();
        stored.text = turn.text();
        stored.quotedText = turn.quotedText();
        stored.intent = turn.intent();
        stored.aiVerdict = turn.aiVerdict();
        stored.correction = turn.correction();
        return stored;
    }

    UserTurn toUserTurn() {
        return new UserTurn(turnIndex, text, quotedText, intent, aiVerdict, correction);
    }

    public int getTurnIndex() {
        return turnIndex;
    }
}
