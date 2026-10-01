package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import org.junit.jupiter.api.Test;

/** 안정도 사다리 (스펙 §6.4.6)와 기억 강도 상한 (§6.4.7). */
class QuestionLadderTest {

    static final QuestionLadder LADDER = new QuestionLadder(3, 14);

    @Test
    void levelFollowsStability() {
        assertThat(LADDER.level(null)).isEqualTo(1);
        assertThat(LADDER.level(0.5)).isEqualTo(1);
        assertThat(LADDER.level(2.99)).isEqualTo(1);
        assertThat(LADDER.level(3.0)).isEqualTo(2);
        assertThat(LADDER.level(13.9)).isEqualTo(2);
        assertThat(LADDER.level(14.0)).isEqualTo(3);
        assertThat(LADDER.level(90.0)).isEqualTo(3);
    }

    @Test
    void typePerKindAndLevel() {
        assertThat(LADDER.typeFor(1.0, MemoryItemKind.FACT, 3)).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(LADDER.typeFor(1.0, MemoryItemKind.CONFUSION, 3)).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(LADDER.typeFor(5.0, MemoryItemKind.FACT, 3)).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(LADDER.typeFor(5.0, MemoryItemKind.CONFUSION, 3)).as("S 3일 이상부터 오류 찾기").isEqualTo(QuestionType.ERROR_FINDING);
        assertThat(LADDER.typeFor(20.0, MemoryItemKind.WARNING, 3)).isEqualTo(QuestionType.CASE_JUDGMENT);
        assertThat(LADDER.typeFor(20.0, MemoryItemKind.PRACTICE, 3)).isEqualTo(QuestionType.CASE_APPLICATION);
        assertThat(LADDER.typeFor(20.0, MemoryItemKind.FACT, 3)).isEqualTo(QuestionType.CASE_APPLICATION);
        assertThat(LADDER.typeFor(20.0, MemoryItemKind.CONFUSION, 3)).isEqualTo(QuestionType.ERROR_FINDING);
    }

    @Test
    void memoryStrengthCapsTheLevel() {
        int light = MemoryStrength.LIGHT.maxQuestionLevel();
        int master = MemoryStrength.MASTER.maxQuestionLevel();

        assertThat(LADDER.typeFor(30.0, MemoryItemKind.WARNING, light)).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(LADDER.typeFor(30.0, MemoryItemKind.WARNING, master)).isEqualTo(QuestionType.CASE_JUDGMENT);
        assertThat(LADDER.typeFor(30.0, MemoryItemKind.FACT, 1)).isEqualTo(QuestionType.MULTIPLE_CHOICE);
    }

    @Test
    void rejectsInvalidBoundaries() {
        assertThatThrownBy(() -> new QuestionLadder(10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuestionLadder(0, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
