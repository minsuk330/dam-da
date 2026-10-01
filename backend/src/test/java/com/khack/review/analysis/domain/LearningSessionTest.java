package com.khack.review.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningSessionTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    static final List<UserTurn> TURNS = List.of(
            new UserTurn(1, "표준오차가 뭐야?", null, Intent.info_request, null, null),
            new UserTurn(2, "그럼 표본이 크면 표준편차도 줄어?", null, Intent.understanding_check, AiVerdict.corrected, "표준오차가 준다"),
            new UserTurn(3, "EXPLAIN은 어떻게 봐?", null, Intent.info_request, null, null),
            new UserTurn(4, "복습에 넣어줘", null, Intent.meta, null, null));

    static LearningSession create(List<com.khack.review.collection.domain.ReviewUnit> units) {
        return LearningSession.create(7L, 11L, new SessionInput(TURNS, units, "경영통계"), NOW);
    }

    @Test
    void eachKeyPointAndConfusionPointBecomesOneMemoryItem() {
        LearningSession session = create(List.of(new com.khack.review.collection.domain.ReviewUnit("표준오차",
                List.of(new KeyPoint("표준오차는 표본평균의 퍼짐", List.of(1, 2), null),
                        new KeyPoint("표본이 커져도 표준편차는 줄지 않는다", List.of(2), FactKind.warning),
                        new KeyPoint("EXPLAIN의 type ALL을 확인한다", List.of(3), FactKind.practice)),
                List.of(new ConfusionPoint(2, "표본이 크면 표준편차가 준다")))));

        assertThat(session.getStatus()).isEqualTo(LearningSessionStatus.RECEIVED);
        assertThat(session.getUserId()).isEqualTo(7L);
        assertThat(session.getConversationId()).isEqualTo(11L);
        assertThat(session.items()).extracting(MemoryItem::getKind)
                .containsExactly(MemoryItemKind.FACT, MemoryItemKind.WARNING, MemoryItemKind.PRACTICE, MemoryItemKind.CONFUSION);
        assertThat(session.items().get(3).getContent()).isEqualTo("표본이 크면 표준편차가 준다");
        assertThat(session.items()).allMatch(item -> item.getStatus() == MemoryItemStatus.NEW);
        assertThat(session.getUnits().get(0).evidenceTurns()).containsExactly(1, 2, 3);
    }

    @Test
    void metaTurnsAreNeverSourcesAndEmptyItemsOrUnitsAreDropped() {
        LearningSession session = create(List.of(
                new com.khack.review.collection.domain.ReviewUnit("저장 기능",
                        List.of(new KeyPoint("복습 앱에 저장된다", List.of(4), null)), null),
                new com.khack.review.collection.domain.ReviewUnit("표준오차",
                        List.of(new KeyPoint("표준오차는 표본평균의 퍼짐", List.of(1, 4), null),
                                new KeyPoint("메타만 근거", List.of(4), null)), null)));

        assertThat(session.getUnits()).extracting(ReviewUnit::getTitle).containsExactly("표준오차");
        assertThat(session.items()).singleElement().satisfies(item -> {
            assertThat(item.getContent()).isEqualTo("표준오차는 표본평균의 퍼짐");
            assertThat(item.getSourceTurns()).containsExactly(1);
        });
    }

    @Test
    void confusionPointWithoutUserBeliefIsSkipped() {
        LearningSession session = create(List.of(new com.khack.review.collection.domain.ReviewUnit("표준오차",
                List.of(new KeyPoint("표준오차는 표본평균의 퍼짐", List.of(1), null)),
                List.of(new ConfusionPoint(2, null), new ConfusionPoint(2, " ")))));

        assertThat(session.items()).extracting(MemoryItem::getKind).containsExactly(MemoryItemKind.FACT);
    }

    @Test
    void excludingAUnitExcludesItsItems() {
        LearningSession session = create(List.of(new com.khack.review.collection.domain.ReviewUnit("표준오차",
                List.of(new KeyPoint("표준오차는 표본평균의 퍼짐", List.of(1), null)),
                List.of(new ConfusionPoint(2, "표본이 크면 표준편차가 준다")))));

        session.getUnits().get(0).exclude();

        assertThat(session.getUnits().get(0).isExcluded()).isTrue();
        assertThat(session.items()).allMatch(item -> item.getStatus() == MemoryItemStatus.EXCLUDED);
    }

    @Test
    void statusMovesOnlyInOrder() {
        LearningSession session = create(List.of(new com.khack.review.collection.domain.ReviewUnit("표준오차",
                List.of(new KeyPoint("표준오차는 표본평균의 퍼짐", List.of(1), null)), null)));

        assertThatThrownBy(() -> session.moveTo(LearningSessionStatus.CONFIRMED)).isInstanceOf(IllegalStateException.class);

        for (LearningSessionStatus next : List.of(LearningSessionStatus.REVIEWING, LearningSessionStatus.AWAITING_CONFIRMATION,
                LearningSessionStatus.CONFIRMED, LearningSessionStatus.QUESTIONS_READY, LearningSessionStatus.IN_PROGRESS)) {
            session.moveTo(next);
            assertThat(session.getStatus()).isEqualTo(next);
        }
        assertThatThrownBy(() -> session.moveTo(LearningSessionStatus.RECEIVED)).isInstanceOf(IllegalStateException.class);
    }

    static LearningSession awaitingConfirmation() {
        LearningSession session = create(List.of(new com.khack.review.collection.domain.ReviewUnit("표준오차",
                List.of(new KeyPoint("표준오차는 표본평균의 퍼짐", List.of(1, 2), null),
                        new KeyPoint("EXPLAIN의 type ALL을 확인한다", List.of(3), FactKind.practice)),
                List.of(new ConfusionPoint(2, "표본이 크면 표준편차가 준다")))));
        session.moveTo(LearningSessionStatus.REVIEWING);
        session.moveTo(LearningSessionStatus.AWAITING_CONFIRMATION);
        return session;
    }

    @Test
    void editsAreAllowedOnlyWhileAwaitingConfirmation() {
        LearningSession received = create(List.of(new com.khack.review.collection.domain.ReviewUnit("표준오차",
                List.of(new KeyPoint("표준오차는 표본평균의 퍼짐", List.of(1), null)), null)));

        assertThatThrownBy(received::requireEditable).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> received.turnInserted(2, List.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> received.confirm(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insertedTurnShiftsLaterSources() {
        LearningSession session = awaitingConfirmation();

        session.turnInserted(2, List.of());

        assertThat(session.items()).extracting(MemoryItem::getSourceTurns)
                .containsExactly(List.of(1, 3), List.of(4), List.of(3));
    }

    @Test
    void turnBecomingMetaLeavesSourcesAndExcludesItemsWithoutEvidence() {
        LearningSession session = awaitingConfirmation();

        session.turnBecameMeta(3);

        MemoryItem practice = session.items().get(1);
        assertThat(practice.getSourceTurns()).isEmpty();
        assertThat(practice.isExcluded()).isTrue();
        assertThat(session.items().get(0).isExcluded()).isFalse();
    }

    @Test
    void confirmingClosesEditsAndOpensQuestionGeneration() {
        LearningSession session = awaitingConfirmation();
        assertThat(session.isConfirmed()).isFalse();

        session.confirm(NOW);

        assertThat(session.getStatus()).isEqualTo(LearningSessionStatus.CONFIRMED);
        assertThat(session.getConfirmedAt()).isEqualTo(NOW);
        assertThat(session.isConfirmed()).isTrue();
        assertThatThrownBy(session::requireEditable).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void warningsPointAtTurnsTheUserCanFix() {
        LearningSession session = awaitingConfirmation();
        List<UserTurn> turns = List.of(
                new UserTurn(1, "표준오차가 뭐야?", null, Intent.info_request, null, null),
                new UserTurn(2, "그럼 표본이 크면 표준편차도 줄어?", null, Intent.understanding_check, AiVerdict.corrected, null),
                new UserTurn(3, "EXPLAIN은 어떻게 봐?", null, Intent.info_request, null, null),
                new UserTurn(4, "이 경우도 같아?", null, Intent.understanding_check, AiVerdict.partial, "조건이 다르다"),
                new UserTurn(5, "복습에 넣어줘", null, Intent.meta, null, null));

        assertThat(ConfirmationWarnings.of(session, turns)).containsExactly(
                "2번째 메시지: AI가 바로잡았다고 표시됐지만 바로잡은 내용이 비어 있어요.",
                "4번째 메시지: 어느 기억할 내용에도 연결되지 않았어요.");

        session.getUnits().get(0).exclude();
        assertThat(ConfirmationWarnings.of(session, turns)).hasSize(5);
    }
}
