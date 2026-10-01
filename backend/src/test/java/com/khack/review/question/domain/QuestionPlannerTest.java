package com.khack.review.question.domain;

import static com.khack.review.question.domain.LearningGoal.APPLY_TO_CASE;
import static com.khack.review.question.domain.LearningGoal.CORRECT_MISCONCEPTION;
import static com.khack.review.question.domain.LearningGoal.DISTINGUISH_CONCEPTS;
import static com.khack.review.question.domain.LearningGoal.EXPLAIN_IN_OWN_WORDS;
import static com.khack.review.question.domain.LearningGoal.JUDGE_CONDITIONS;
import static com.khack.review.question.domain.LearningGoal.REMEMBER_CORE;
import static com.khack.review.question.domain.LearningGoal.UNDERSTAND_PRINCIPLE;
import static com.khack.review.question.domain.Sources.confusion;
import static com.khack.review.question.domain.Sources.meta;
import static com.khack.review.question.domain.Sources.point;
import static com.khack.review.question.domain.Sources.source;
import static com.khack.review.question.domain.Sources.turn;
import static com.khack.review.question.domain.Sources.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionPlannerTest {

    @Test
    void rememberCoreAsksEveryFactAsShortAnswer() {
        QuestionPlan plan = QuestionPlanner.plan(Sources.etag(), List.of(REMEMBER_CORE));

        assertThat(plan.targets()).hasSize(4).allSatisfy(t -> {
            assertThat(t.type()).isEqualTo(QuestionType.SHORT_ANSWER);
            assertThat(t.itemKind()).isEqualTo(ItemKind.KEY_POINT);
        });
        assertThat(plan.targets()).extracting(QuestionTarget::unitIndex, QuestionTarget::itemIndex)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0, 0), org.assertj.core.groups.Tuple.tuple(0, 1),
                        org.assertj.core.groups.Tuple.tuple(0, 2), org.assertj.core.groups.Tuple.tuple(1, 0));
    }

    @Test
    void confusionPointsComeFirstAsErrorFinding() {
        QuestionPlan plan = QuestionPlanner.plan(Sources.etag(), List.of(UNDERSTAND_PRINCIPLE, CORRECT_MISCONCEPTION));

        QuestionTarget first = plan.targets().get(0);
        assertThat(first.goal()).isEqualTo(CORRECT_MISCONCEPTION);
        assertThat(first.type()).isEqualTo(QuestionType.ERROR_FINDING);
        assertThat(first.itemKind()).isEqualTo(ItemKind.CONFUSION_POINT);
        assertThat(first.evidenceTurns()).containsExactly(2);
        assertThat(plan.targets()).hasSize(3);
        assertThat(plan.emptyGoals()).isEmpty();
    }

    @Test
    void perUnitGoalsTargetTheMostDiscussedFact() {
        QuestionPlan plan = QuestionPlanner.plan(Sources.etag(), List.of(UNDERSTAND_PRINCIPLE));

        assertThat(plan.targets()).hasSize(2);
        QuestionTarget unit0 = plan.targets().get(0);
        assertThat(unit0.type()).isEqualTo(QuestionType.DESCRIPTIVE);
        assertThat(unit0.itemIndex()).isEqualTo(1);
        assertThat(unit0.evidenceTurns()).containsExactly(1, 3, 4);
        assertThat(plan.targets().get(1).itemIndex()).isZero();
    }

    @Test
    void conditionsUseWarningsAndApplicationPrefersPractice() {
        QuestionPlan plan = QuestionPlanner.plan(Sources.etag(), List.of(JUDGE_CONDITIONS, APPLY_TO_CASE));

        assertThat(plan.targets()).filteredOn(t -> t.goal() == JUDGE_CONDITIONS).singleElement().satisfies(t -> {
            assertThat(t.type()).isEqualTo(QuestionType.CASE_JUDGMENT);
            assertThat(t.unitIndex()).isEqualTo(1);
            assertThat(t.itemIndex()).isEqualTo(1);
        });
        assertThat(plan.targets()).filteredOn(t -> t.goal() == APPLY_TO_CASE)
                .extracting(QuestionTarget::type, QuestionTarget::unitIndex, QuestionTarget::itemIndex)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(QuestionType.CASE_APPLICATION, 0, 1),
                        org.assertj.core.groups.Tuple.tuple(QuestionType.CASE_APPLICATION, 1, 2));
    }

    @Test
    void distinguishNeedsTwoKeyPointsInAUnit() {
        QuestionSource source = source(List.of(turn(1)), unit(List.of(point(PointKind.FACT, 1))));

        QuestionPlan plan = QuestionPlanner.plan(source, List.of(DISTINGUISH_CONCEPTS, EXPLAIN_IN_OWN_WORDS));

        assertThat(plan.targets()).extracting(QuestionTarget::goal).containsExactly(EXPLAIN_IN_OWN_WORDS);
        assertThat(plan.emptyGoals()).containsOnlyKeys(DISTINGUISH_CONCEPTS);
    }

    @Test
    void goalWithoutMatchingItemsIsReportedWithReason() {
        QuestionSource source = source(List.of(turn(1)), unit(List.of(point(PointKind.FACT, 1))));

        QuestionPlan plan = QuestionPlanner.plan(source, List.of(JUDGE_CONDITIONS, CORRECT_MISCONCEPTION));

        assertThat(plan.targets()).isEmpty();
        assertThat(plan.emptyGoals()).containsOnlyKeys(JUDGE_CONDITIONS, CORRECT_MISCONCEPTION);
        assertThat(plan.emptyGoals().get(JUDGE_CONDITIONS)).contains("warning");
    }

    @Test
    void metaTurnsAreNotEvidence() {
        QuestionSource source = source(List.of(turn(1), meta(2)),
                unit(List.of(point(PointKind.FACT, 1, 2), point(PointKind.FACT, 2)), confusion(2)));

        QuestionPlan plan = QuestionPlanner.plan(source, List.of(REMEMBER_CORE, CORRECT_MISCONCEPTION));

        assertThat(plan.targets()).singleElement().satisfies(t -> {
            assertThat(t.itemIndex()).isZero();
            assertThat(t.evidenceTurns()).containsExactly(1);
        });
        assertThat(plan.skipped()).extracting(QuestionPlan.Skipped::itemKind, QuestionPlan.Skipped::itemIndex)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ItemKind.KEY_POINT, 1),
                        org.assertj.core.groups.Tuple.tuple(ItemKind.CONFUSION_POINT, 0));
    }

    @Test
    void confusionWithoutCorrectionIsSkipped() {
        QuestionSource source = source(List.of(turn(1)), unit(List.of(point(PointKind.FACT, 1)),
                new QuestionSource.Confusion(1, "잘못 믿은 내용", null)));

        QuestionPlan plan = QuestionPlanner.plan(source, List.of(CORRECT_MISCONCEPTION));

        assertThat(plan.targets()).isEmpty();
        assertThat(plan.skipped()).singleElement()
                .satisfies(s -> assertThat(s.itemKind()).isEqualTo(ItemKind.CONFUSION_POINT));
    }

    @Test
    void overflowBeyondFirstStudyLimitIsDeferredWithConfusionsKept() {
        List<QuestionSource.Turn> turns = new ArrayList<>();
        List<QuestionSource.KeyPoint> points = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            turns.add(turn(i));
            points.add(point(PointKind.FACT, i));
        }
        QuestionSource source = source(turns, unit(points, confusion(13), confusion(14)));

        QuestionPlan plan = QuestionPlanner.plan(source, List.of(REMEMBER_CORE, CORRECT_MISCONCEPTION));

        assertThat(plan.targets()).hasSize(QuestionPlanner.FIRST_STUDY_LIMIT);
        assertThat(plan.targets().subList(0, 2)).allSatisfy(t -> assertThat(t.goal()).isEqualTo(CORRECT_MISCONCEPTION));
        assertThat(plan.deferred()).hasSize(4).allSatisfy(t -> assertThat(t.goal()).isEqualTo(REMEMBER_CORE));
    }

    @Test
    void rejectsZeroOrMoreThanThreeGoals() {
        assertThatThrownBy(() -> QuestionPlanner.plan(Sources.etag(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuestionPlanner.plan(Sources.etag(),
                List.of(REMEMBER_CORE, UNDERSTAND_PRINCIPLE, DISTINGUISH_CONCEPTS, JUDGE_CONDITIONS)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
