package com.khack.review.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 신규 항목의 문제 유형은 학습 목표로 정한다 (스펙 §6.4.4, §7.8). */
class NewItemGoalTypesTest {

    @Test
    void firstGoalThatMatchesTheKindWins() {
        List<LearningGoal> goals = List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.PRINCIPLE, LearningGoal.CONDITION);

        assertThat(NewItemGoalTypes.fromGoals(goals, MemoryItemKind.CONFUSION)).isEqualTo(QuestionType.ERROR_FINDING);
        assertThat(NewItemGoalTypes.fromGoals(goals, MemoryItemKind.FACT)).isEqualTo(QuestionType.ESSAY);
        assertThat(NewItemGoalTypes.fromGoals(goals, MemoryItemKind.WARNING)).isEqualTo(QuestionType.CASE_JUDGMENT);
    }

    @Test
    void fallsBackToKindDefaultWhenNoGoalMatchesOrNoGoalsChosen() {
        assertThat(NewItemGoalTypes.fromGoals(List.of(LearningGoal.CORRECT_MISCONCEPTION), MemoryItemKind.FACT))
                .isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(NewItemGoalTypes.typeFor(null, 1L, MemoryItemKind.PRACTICE)).isEqualTo(QuestionType.CASE_APPLICATION);
        assertThat(NewItemGoalTypes.typeFor(null, 1L, MemoryItemKind.WARNING)).isEqualTo(QuestionType.CASE_JUDGMENT);
        assertThat(NewItemGoalTypes.typeFor(null, 1L, MemoryItemKind.CONFUSION)).isEqualTo(QuestionType.ERROR_FINDING);
    }

    @Test
    void keyRecallGivesShortAnswerAndDistinguishGivesMultipleChoice() {
        assertThat(NewItemGoalTypes.fromGoals(List.of(LearningGoal.KEY_RECALL), MemoryItemKind.FACT)).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(NewItemGoalTypes.fromGoals(List.of(LearningGoal.DISTINGUISH), MemoryItemKind.FACT))
                .isEqualTo(QuestionType.MULTIPLE_CHOICE);
    }
}
