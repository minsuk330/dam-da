package com.khack.review.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.practice.domain.FeedbackRules.Plan;
import com.khack.review.practice.domain.FeedbackRules.State;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeedbackRulesTest {

    static State state(PracticeKind kind, AttemptOutcome outcome) {
        return new State(kind, false, outcome, false, false, false, false, false, false);
    }

    @Test
    void firstStudyWrongOffersHintOrExplanationAndDefaultsToHint() {
        Plan plan = FeedbackRules.plan(state(PracticeKind.FIRST_STUDY, AttemptOutcome.WRONG));

        assertThat(plan.allowed()).containsExactlyInAnyOrder(FeedbackAction.GIVE_HINT, FeedbackAction.EXPLAIN_CONCEPT);
        assertThat(plan.fallback()).isEqualTo(FeedbackAction.GIVE_HINT);
        assertThat(plan.needsJudgment()).isTrue();
    }

    @Test
    void afterHintAndRetryWrongOnlyExplanationRemains() {
        State s = new State(PracticeKind.FIRST_STUDY, false, AttemptOutcome.WRONG, true, true, false, false, false, false);

        assertThat(FeedbackRules.plan(s).allowed()).containsExactly(FeedbackAction.EXPLAIN_CONCEPT);
    }

    @Test
    void waitsForRetryWhileContentAidIsNewerThanTheAnswer() {
        State s = new State(PracticeKind.FIRST_STUDY, false, AttemptOutcome.WRONG, true, true, false, true, false, false);

        Plan plan = FeedbackRules.plan(s);

        assertThat(plan.allowed()).containsExactly(FeedbackAction.RETRY);
        assertThat(plan.needsJudgment()).isFalse();
    }

    @Test
    void afterExplanationFirstStudyAdvances() {
        State s = new State(PracticeKind.FIRST_STUDY, false, AttemptOutcome.WRONG, true, true, true, false, true, false);

        assertThat(FeedbackRules.plan(s).allowed()).containsExactly(FeedbackAction.ADVANCE);
    }

    @Test
    void dailyWrongRequeuesToday() {
        Plan plan = FeedbackRules.plan(state(PracticeKind.DAILY, AttemptOutcome.WRONG));

        assertThat(plan.allowed()).containsExactlyInAnyOrder(FeedbackAction.RELEARN_TODAY, FeedbackAction.EXPLAIN_CONCEPT);
        assertThat(plan.fallback()).isEqualTo(FeedbackAction.RELEARN_TODAY);
    }

    @Test
    void dailyRelearnHappensOnlyOnce() {
        State queued = new State(PracticeKind.DAILY, false, AttemptOutcome.WRONG, false, false, false, false, true, false);

        Plan plan = FeedbackRules.plan(queued);

        assertThat(plan.allowed()).doesNotContain(FeedbackAction.RELEARN_TODAY);
        assertThat(plan.fallback()).isEqualTo(FeedbackAction.ADVANCE);
    }

    @Test
    void dailyCapStopsRequeue() {
        State capped = new State(PracticeKind.DAILY, false, AttemptOutcome.WRONG, false, false, true, false, false, true);

        assertThat(FeedbackRules.plan(capped)).isEqualTo(new Plan(Set.of(FeedbackAction.ADVANCE), FeedbackAction.ADVANCE));
    }

    @Test
    void recheckPresentationIsNeverRequeued() {
        for (PracticeKind kind : PracticeKind.values()) {
            State s = new State(kind, true, AttemptOutcome.WRONG, false, false, false, false, false, false);

            assertThat(FeedbackRules.plan(s).allowed()).containsExactly(FeedbackAction.ADVANCE);
        }
    }

    @Test
    void correctAfterAidMayBeRecheckedButUnassistedCorrectJustAdvances() {
        assertThat(FeedbackRules.plan(state(PracticeKind.FIRST_STUDY, AttemptOutcome.CORRECT)).allowed())
                .containsExactly(FeedbackAction.ADVANCE);
        State aided = new State(PracticeKind.FIRST_STUDY, false, AttemptOutcome.CORRECT, true, true, false, false, false, false);

        Plan plan = FeedbackRules.plan(aided);

        assertThat(plan.allowed()).containsExactlyInAnyOrder(FeedbackAction.ADVANCE, FeedbackAction.RELEARN_TODAY);
        assertThat(plan.fallback()).isEqualTo(FeedbackAction.ADVANCE);
    }

    @Test
    void uncertainAndAmbiguousOutcomesAreNotJudgedByJev() {
        assertThat(FeedbackRules.plan(state(PracticeKind.DAILY, AttemptOutcome.UNCERTAIN)).allowed())
                .containsExactly(FeedbackAction.REQUEST_CONFIRMATION);
        assertThat(FeedbackRules.plan(state(PracticeKind.FIRST_STUDY, AttemptOutcome.QUESTION_AMBIGUOUS)).allowed())
                .containsExactly(FeedbackAction.GENERATE_VARIANT);
    }

    @Test
    void pathIsDerivedFromTheStrongestAidBeforeTheCorrectAnswer() {
        assertThat(FeedbackRules.path(AttemptOutcome.CORRECT, 0, false, false)).isEqualTo(FeedbackPath.INDEPENDENT);
        assertThat(FeedbackRules.path(AttemptOutcome.CORRECT, 1, true, false)).isEqualTo(FeedbackPath.AFTER_HINT);
        assertThat(FeedbackRules.path(AttemptOutcome.CORRECT, 2, true, true)).isEqualTo(FeedbackPath.AFTER_EXPLANATION);
        assertThat(FeedbackRules.path(AttemptOutcome.WRONG, 1, false, false)).isNull();
        assertThat(FeedbackRules.path(AttemptOutcome.WRONG, 2, true, true)).isEqualTo(FeedbackPath.REPEATED_WRONG);
    }
}
