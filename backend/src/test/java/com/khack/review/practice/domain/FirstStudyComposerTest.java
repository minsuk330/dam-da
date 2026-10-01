package com.khack.review.practice.domain;

import static com.khack.review.analysis.domain.MemoryItemKind.CONFUSION;
import static com.khack.review.analysis.domain.MemoryItemKind.FACT;
import static com.khack.review.analysis.domain.MemoryItemKind.PRACTICE;
import static com.khack.review.analysis.domain.MemoryItemKind.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.practice.domain.FirstStudyComposer.Composition;
import com.khack.review.practice.domain.FirstStudyComposer.GoalSummary;
import com.khack.review.practice.domain.FirstStudyComposer.Item;
import com.khack.review.practice.domain.FirstStudyComposer.PlannedQuestion;
import com.khack.review.practice.domain.FirstStudyComposer.Unit;
import com.khack.review.question.domain.QuestionType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FirstStudyComposerTest {

    /** 단위 1: fact 2, warning 1, practice 1, confusion 1 / 단위 2: fact 1 */
    static final List<Unit> UNITS = List.of(
            new Unit(1L, List.of(new Item(11L, FACT), new Item(12L, WARNING), new Item(13L, FACT),
                    new Item(14L, PRACTICE), new Item(15L, CONFUSION))),
            new Unit(2L, List.of(new Item(21L, FACT))));

    static Composition compose(LearningGoal... goals) {
        return FirstStudyComposer.compose(List.of(goals), UNITS, 12);
    }

    static List<String> questions(Composition c) {
        return c.questions().stream().map(q -> q.goal().number() + ":" + q.memoryItemId() + ":" + q.type()).toList();
    }

    @Test
    void eachGoalFollowsTheSpecTable() {
        assertThat(questions(compose(LearningGoal.KEY_RECALL)))
                .containsExactly("1:11:SHORT_ANSWER", "1:13:SHORT_ANSWER", "1:21:SHORT_ANSWER");
        assertThat(questions(compose(LearningGoal.PRINCIPLE))).containsExactly("2:11:ESSAY", "2:21:ESSAY");
        assertThat(compose(LearningGoal.DISTINGUISH).questions()).singleElement()
                .isEqualTo(new PlannedQuestion(LearningGoal.DISTINGUISH, 11L, 12L, QuestionType.MULTIPLE_CHOICE));
        assertThat(questions(compose(LearningGoal.CONDITION))).containsExactly("4:12:CASE_JUDGMENT");
        assertThat(questions(compose(LearningGoal.APPLY_CASE))).containsExactly("5:14:CASE_APPLICATION", "5:21:CASE_APPLICATION");
        assertThat(questions(compose(LearningGoal.CORRECT_MISCONCEPTION))).containsExactly("6:15:ERROR_FINDING");
        assertThat(questions(compose(LearningGoal.EXPLAIN_OWN_WORDS))).containsExactly("7:11:ESSAY", "7:21:ESSAY");
    }

    @Test
    void goalsWithoutTargetsHaveZeroQuestionsAndAReason() {
        Composition c = FirstStudyComposer.compose(List.of(LearningGoal.CONDITION, LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.KEY_RECALL),
                List.of(new Unit(2L, List.of(new Item(21L, FACT)))), 12);

        assertThat(c.goals()).extracting(GoalSummary::planned).containsExactly(0, 0, 1);
        assertThat(c.goals().get(0).reason()).contains("warning");
        assertThat(c.goals().get(1).reason()).contains("헷갈린 지점");
        assertThat(c.goals().get(2).reason()).isNull();
    }

    @Test
    void confusionQuestionsComeFirstAndSurviveTheCap() {
        List<Item> facts = new ArrayList<>();
        for (long id = 100; id < 115; id++) {
            facts.add(new Item(id, FACT));
        }
        facts.add(new Item(200L, CONFUSION));
        Composition c = FirstStudyComposer.compose(List.of(LearningGoal.KEY_RECALL, LearningGoal.CORRECT_MISCONCEPTION),
                List.of(new Unit(9L, facts)), 12);

        assertThat(c.questions()).hasSize(12);
        assertThat(c.questions().get(0).memoryItemId()).isEqualTo(200L);
        assertThat(c.goals()).extracting(GoalSummary::candidates).containsExactly(15, 1);
        assertThat(c.goals()).extracting(GoalSummary::planned).containsExactly(11, 1);
        assertThat(c.unplannedItemIds()).containsExactly(111L, 112L, 113L, 114L);
    }

    @Test
    void itemsNotInAnyQuestionStayNew() {
        Composition c = compose(LearningGoal.CORRECT_MISCONCEPTION);

        assertThat(c.unplannedItemIds()).containsExactly(11L, 12L, 13L, 14L, 21L);
    }

    @Test
    void distinguishNeedsTwoKeyPointsInAUnit() {
        Composition c = FirstStudyComposer.compose(List.of(LearningGoal.DISTINGUISH),
                List.of(new Unit(2L, List.of(new Item(21L, FACT), new Item(22L, CONFUSION)))), 12);

        assertThat(c.questions()).isEmpty();
        assertThat(c.goals().get(0).reason()).contains("2개 이상");
    }
}
