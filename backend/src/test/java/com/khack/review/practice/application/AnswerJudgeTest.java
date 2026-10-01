package com.khack.review.practice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.memory.domain.AnswerVerdict;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswerJudgeTest {

    static final AnswerJudgePolicy POLICY = new AnswerJudgePolicy(0.5, 3, Duration.ofMillis(1));

    static final AnswerJudgeState FACT = new AnswerJudgeState("표준오차란?", "SHORT_ANSWER", List.of("표본평균의 퍼짐"),
            "표본평균의 퍼짐", "표본평균이 흩어진 정도", "표준오차는 표본평균의 퍼짐", null, null);

    static final AnswerJudgeState CONFUSION = new AnswerJudgeState("틀린 곳은?", "ERROR_FINDING", List.of("표준편차는 줄지 않는다"),
            "표준편차는 줄지 않는다", "표본이 크면 표준편차가 준다", "표본이 크면 표준편차가 준다", "표본이 크면 표준편차가 준다",
            "표준오차가 준다");

    static JevResult result(String verdict, double confidence, double omission, double contradiction, double misread,
            Double repeats, double offTarget) {
        Map<String, JevAnswer> answers = new LinkedHashMap<>();
        answers.put(AnswerJudgeQuestions.VERDICT, new JevAnswer.Choice(verdict, Map.of(verdict, confidence), confidence));
        answers.put(AnswerJudgeQuestions.OMISSION, new JevAnswer.Noul(omission));
        answers.put(AnswerJudgeQuestions.CONTRADICTION, new JevAnswer.Noul(contradiction));
        answers.put(AnswerJudgeQuestions.MISREAD, new JevAnswer.Noul(misread));
        if (repeats != null) {
            answers.put(AnswerJudgeQuestions.REPEATS_USER_BELIEF, new JevAnswer.Noul(repeats));
        }
        answers.put(AnswerJudgeQuestions.OFF_TARGET_ERROR, new JevAnswer.Noul(offTarget));
        return new JevResult("fake-model", answers);
    }

    static AnswerJudge judge(FakeJevPort jev, List<Duration> slept) {
        return new AnswerJudge(jev, POLICY, slept::add);
    }

    @Test
    void readsAllAnswersInOneCall() {
        FakeJevPort jev = new FakeJevPort().willReturn(result("met", 0.82, 0.1, 0.05, 0.02, null, 0.1));

        AnswerJudge.Outcome outcome = judge(jev, new ArrayList<>()).judge(FACT);

        assertThat(outcome.judged()).isTrue();
        assertThat(outcome.jev().verdict()).isEqualTo(AnswerVerdict.MET);
        assertThat(outcome.jev().verdictConfidence()).isEqualTo(0.82);
        assertThat(outcome.jev().misread()).isEqualTo(0.02);
        assertThat(outcome.jev().repeatsUserBelief()).isNull();
        assertThat(outcome.jev().model()).isEqualTo("fake-model");
        assertThat(jev.calls()).singleElement().satisfies(call -> {
            assertThat(call.state()).isEqualTo(FACT);
            assertThat(call.questions()).containsOnlyKeys(AnswerJudgeQuestions.VERDICT, AnswerJudgeQuestions.OMISSION,
                    AnswerJudgeQuestions.CONTRADICTION, AnswerJudgeQuestions.MISREAD, AnswerJudgeQuestions.OFF_TARGET_ERROR);
        });
    }

    @Test
    void asksAboutTheUserBeliefOnlyForConfusionItems() {
        FakeJevPort jev = new FakeJevPort().willReturn(result("not_met", 0.9, 0.2, 0.9, 0.1, 0.85, 0.1));

        AnswerJudge.Outcome outcome = judge(jev, new ArrayList<>()).judge(CONFUSION);

        assertThat(outcome.jev().verdict()).isEqualTo(AnswerVerdict.NOT_MET);
        assertThat(outcome.jev().repeatsUserBelief()).isEqualTo(0.85);
        assertThat(jev.calls().get(0).questions()).containsKey(AnswerJudgeQuestions.REPEATS_USER_BELIEF);
    }

    @Test
    void mapsUnableToJudge() {
        FakeJevPort jev = new FakeJevPort().willReturn(result("unable_to_judge", 0.7, 0.1, 0.1, 0.1, null, 0.1));

        assertThat(judge(jev, new ArrayList<>()).judge(FACT).jev().verdict()).isEqualTo(AnswerVerdict.UNABLE_TO_JUDGE);
    }

    @Test
    void retriesOverloadAndRateLimitThenSucceeds() {
        List<Duration> slept = new ArrayList<>();
        FakeJevPort jev = new FakeJevPort()
                .willRespond(new JevCallException(429, "rate limit", null), new JevCallException(529, "overloaded", null))
                .willReturn(result("met", 0.9, 0.1, 0.1, 0.1, null, 0.1));

        AnswerJudge.Outcome outcome = judge(jev, slept).judge(FACT);

        assertThat(outcome.judged()).isTrue();
        assertThat(jev.calls()).hasSize(3);
        assertThat(slept).containsExactly(Duration.ofMillis(1), Duration.ofMillis(2));
    }

    @Test
    void failsWhenRetriesRunOutOrTheErrorIsNotRetryable() {
        FakeJevPort overloaded = new FakeJevPort().willFail(new JevCallException(529, "overloaded", null));
        AnswerJudge.Outcome exhausted = judge(overloaded, new ArrayList<>()).judge(FACT);
        assertThat(exhausted.judged()).isFalse();
        assertThat(exhausted.failure()).contains("Jev 호출 실패");
        assertThat(overloaded.calls()).hasSize(3);

        FakeJevPort badRequest = new FakeJevPort().willFail(new JevCallException(400, "bad", null));
        assertThat(judge(badRequest, new ArrayList<>()).judge(FACT).judged()).isFalse();
        assertThat(badRequest.calls()).hasSize(1);

        FakeJevPort noKey = new FakeJevPort().willFail(new IllegalStateException("키 없음"));
        assertThat(judge(noKey, new ArrayList<>()).judge(FACT).judged()).isFalse();
    }

    @Test
    void failsWhenTheAnswerCannotBeRead() {
        FakeJevPort unknown = new FakeJevPort().willReturn(result("partial", 0.9, 0.1, 0.1, 0.1, null, 0.1));
        assertThat(judge(unknown, new ArrayList<>()).judge(FACT).failure()).contains("해석 실패");

        FakeJevPort missing = new FakeJevPort().willReturn(new JevResult("fake", Map.of(AnswerJudgeQuestions.VERDICT,
                new JevAnswer.Choice("met", Map.of("met", 0.9), 0.9))));
        assertThat(judge(missing, new ArrayList<>()).judge(FACT).judged()).isFalse();
    }
}
