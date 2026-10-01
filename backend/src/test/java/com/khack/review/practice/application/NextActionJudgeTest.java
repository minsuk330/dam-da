package com.khack.review.practice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.practice.application.NextActionJudge.DecidedBy;
import com.khack.review.practice.domain.AttemptOutcome;
import com.khack.review.practice.domain.FeedbackAction;
import com.khack.review.practice.domain.FeedbackRules;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.question.domain.QuestionType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NextActionJudgeTest {

    static final FeedbackPolicy POLICY = new FeedbackPolicy(0.5, 3, Duration.ofSeconds(1), 5, 2);

    static final NextActionState STATE = new NextActionState(PracticeKind.FIRST_STUDY, false, QuestionType.SHORT_ANSWER,
            "표준오차란?", AttemptOutcome.WRONG, List.of(new NextActionState.Step(AttemptKind.FIRST_UNASSISTED, AttemptOutcome.WRONG)),
            false, false, false);

    static final FeedbackRules.Plan HINT_OR_EXPLAIN = new FeedbackRules.Plan(
            EnumSet.of(FeedbackAction.GIVE_HINT, FeedbackAction.EXPLAIN_CONCEPT), FeedbackAction.GIVE_HINT);

    final FakeJevPort jev = new FakeJevPort();
    final List<Duration> sleeps = new ArrayList<>();
    final NextActionJudge judge = new NextActionJudge(jev, POLICY, sleeps::add);

    static JevResult answer(String choice, double confidence) {
        return new JevResult("fake", Map.of(NextActionQuestions.NEXT_ACTION,
                new JevAnswer.Choice(choice, Map.of(choice, 1.0), confidence)));
    }

    @Test
    void singleOptionIsDecidedByRuleWithoutCallingJev() {
        NextActionJudge.Choice choice = judge.choose(STATE, new FeedbackRules.Plan(EnumSet.of(FeedbackAction.RETRY), FeedbackAction.RETRY));

        assertThat(choice.action()).isEqualTo(FeedbackAction.RETRY);
        assertThat(choice.decidedBy()).isEqualTo(DecidedBy.RULE);
        assertThat(jev.calls()).isEmpty();
    }

    @Test
    void confidentJevChoiceWinsAndOnlyAllowedOptionsAreOffered() {
        jev.willReturn(answer("explain_concept", 0.8));

        NextActionJudge.Choice choice = judge.choose(STATE, HINT_OR_EXPLAIN);

        assertThat(choice.action()).isEqualTo(FeedbackAction.EXPLAIN_CONCEPT);
        assertThat(choice.decidedBy()).isEqualTo(DecidedBy.JEV);
        assertThat(jev.calls()).singleElement().satisfies(call -> {
            assertThat(call.state()).isEqualTo(STATE);
            JevQuestion.Choice question = (JevQuestion.Choice) call.questions().get(NextActionQuestions.NEXT_ACTION);
            assertThat(question.options()).containsOnlyKeys("give_hint", "explain_concept");
        });
    }

    @Test
    void lowConfidenceFallsBackToTheRuleDefault() {
        jev.willReturn(answer("explain_concept", 0.3));

        NextActionJudge.Choice choice = judge.choose(STATE, HINT_OR_EXPLAIN);

        assertThat(choice.action()).isEqualTo(FeedbackAction.GIVE_HINT);
        assertThat(choice.decidedBy()).isEqualTo(DecidedBy.FALLBACK);
        assertThat(choice.detail()).contains("신뢰도");
    }

    @Test
    void anActionOutsideTheAllowedSetFallsBack() {
        jev.willReturn(answer("relearn_today", 0.9));

        NextActionJudge.Choice choice = judge.choose(STATE, HINT_OR_EXPLAIN);

        assertThat(choice.action()).isEqualTo(FeedbackAction.GIVE_HINT);
        assertThat(choice.decidedBy()).isEqualTo(DecidedBy.FALLBACK);
    }

    @Test
    void relearnTodayCanBeChosenWhenAllowed() {
        jev.willReturn(answer("relearn_today", 0.9));
        FeedbackRules.Plan daily = new FeedbackRules.Plan(EnumSet.of(FeedbackAction.RELEARN_TODAY, FeedbackAction.EXPLAIN_CONCEPT),
                FeedbackAction.RELEARN_TODAY);

        assertThat(judge.choose(STATE, daily).action()).isEqualTo(FeedbackAction.RELEARN_TODAY);
    }

    @Test
    void retriesOverloadThenSucceeds() {
        jev.willRespond(new JevCallException(529, "overloaded", null), answer("explain_concept", 0.9));

        NextActionJudge.Choice choice = judge.choose(STATE, HINT_OR_EXPLAIN);

        assertThat(choice.action()).isEqualTo(FeedbackAction.EXPLAIN_CONCEPT);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
        assertThat(jev.calls()).hasSize(2);
    }

    @Test
    void failureFallsBackWithoutThrowing() {
        jev.willFail(new JevCallException(401, "unauthorized", null));

        NextActionJudge.Choice choice = judge.choose(STATE, HINT_OR_EXPLAIN);

        assertThat(choice.action()).isEqualTo(FeedbackAction.GIVE_HINT);
        assertThat(choice.decidedBy()).isEqualTo(DecidedBy.FALLBACK);
        assertThat(jev.calls()).hasSize(1);
    }

    @Test
    void malformedAnswerFallsBack() {
        jev.willReturn(new JevResult("fake", Map.of()));

        assertThat(judge.choose(STATE, HINT_OR_EXPLAIN).decidedBy()).isEqualTo(DecidedBy.FALLBACK);
    }
}
