package com.khack.review.collection.domain;

import static com.khack.review.collection.domain.Fixtures.input;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningConversationEditTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    static LearningConversation connector() {
        return LearningConversation.fromConnector(1L, input(List.of(turn(1, "인덱스가 뭐야"), turn(2, "그럼 순서는?"))), List.of(), NOW);
    }

    @Test
    void userCanFixATranscribedTurn() {
        LearningConversation conversation = connector();

        conversation.editTurn(2, "그러면 컬럼 순서는?", Intent.understanding_check, AiVerdict.corrected, "선두 컬럼부터 쓴다");

        assertThat(conversation.userTurns().get(1))
                .isEqualTo(new UserTurn(2, "그러면 컬럼 순서는?", null, Intent.understanding_check, AiVerdict.corrected, "선두 컬럼부터 쓴다"));
    }

    @Test
    void insertedTurnPushesLaterTurnsBack() {
        LearningConversation conversation = connector();

        int index = conversation.insertTurnAfter(1, "복합 인덱스도 같아?", Intent.understanding_check, AiVerdict.confirmed, null);

        assertThat(index).isEqualTo(2);
        assertThat(conversation.userTurns()).extracting(UserTurn::index, UserTurn::text).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "인덱스가 뭐야"),
                org.assertj.core.groups.Tuple.tuple(2, "복합 인덱스도 같아?"),
                org.assertj.core.groups.Tuple.tuple(3, "그럼 순서는?"));
        assertThat(conversation.insertTurnAfter(0, "맨 앞", Intent.info_request, null, null)).isEqualTo(1);
        assertThat(conversation.userTurns()).extracting(UserTurn::index).containsExactly(1, 2, 3, 4);
    }

    @Test
    void invalidEditsAreRejected() {
        LearningConversation conversation = connector();

        assertThatThrownBy(() -> conversation.editTurn(9, "없음", Intent.info_request, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversation.editTurn(1, " ", Intent.info_request, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> conversation.insertTurnAfter(9, "없는 위치", Intent.info_request, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verbatimTextStaysAsIs() {
        RawConversation raw = RawConversation.fromShareLink(ShareSource.chatgpt, "통계", List.of());
        LearningConversation conversation = LearningConversation.fromTranscript(1L, raw,
                input(List.of(turn(1, "표준오차가 뭐야"))), List.of(), NOW);

        assertThatThrownBy(() -> conversation.editTurn(1, "표준오차는 뭐야", Intent.info_request, null, null))
                .isInstanceOf(IllegalStateException.class);
        conversation.editTurn(1, "표준오차가 뭐야", Intent.understanding_check, AiVerdict.confirmed, null);
        assertThat(conversation.userTurns().get(0).intent()).isEqualTo(Intent.understanding_check);
        assertThatThrownBy(() -> conversation.insertTurnAfter(1, "추가", Intent.info_request, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
