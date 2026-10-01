package com.khack.review.question.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionQualityJudgeTest {

    static final QuestionQualityPolicy POLICY = new QuestionQualityPolicy(0.7, 0.5, 0.5, 0.7, 3, Duration.ofMillis(1), 3);
    static final QuestionQualityState STATE = new QuestionQualityState("표본이 크면 표준편차가 준다", "CONFUSION", "표준오차가 준다",
            List.of(), "ERROR_FINDING", "다음 설명에서 틀린 곳은?", List.of(), null, List.of("표준오차가 준다"), List.of());

    static JevResult result(double grounded, double clarity, double confidence, double duplicate) {
        return new JevResult("fake", Map.of(
                QuestionQualityQuestions.GROUNDED, new JevAnswer.Noul(grounded),
                QuestionQualityQuestions.CLARITY, new JevAnswer.Score(clarity, Map.of(), Map.of(), confidence),
                QuestionQualityQuestions.DUPLICATE, new JevAnswer.Noul(duplicate)));
    }

    static QualityOutcome judge(FakeJevPort jev) {
        return new QuestionQualityJudge(jev, POLICY, duration -> { }).judge(STATE);
    }

    @Test
    void approvesGroundedClearNewQuestions() {
        QualityOutcome outcome = judge(new FakeJevPort().willReturn(result(0.9, 2.0, 0.8, 0.1)));

        assertThat(outcome.approved()).isTrue();
        assertThat(outcome.note()).contains("grounded 0.90");
    }

    @Test
    void rejectsEachFailure() {
        assertThat(judge(new FakeJevPort().willReturn(result(0.5, 2.0, 0.8, 0.1))).note()).startsWith("근거 부족");
        assertThat(judge(new FakeJevPort().willReturn(result(0.9, 0.5, 0.8, 0.1))).note()).startsWith("모호함");
        assertThat(judge(new FakeJevPort().willReturn(result(0.9, 2.0, 0.3, 0.1))).note()).startsWith("명확성 판정 신뢰도 낮음");
        assertThat(judge(new FakeJevPort().willReturn(result(0.9, 2.0, 0.8, 0.8))).note()).startsWith("기존 문제와 중복");
    }

    @Test
    void doesNotApproveWithoutAJudgement() {
        QualityOutcome outcome = judge(new FakeJevPort().willFail(new JevCallException(500, "down", null)));

        assertThat(outcome.approved()).isFalse();
        assertThat(outcome.note()).startsWith("Jev 호출 실패");
    }

    @Test
    void retriesOverloadThenApproves() {
        FakeJevPort jev = new FakeJevPort().willRespond(new JevCallException(529, "overloaded", null))
                .willReturn(result(0.9, 2.0, 0.8, 0.1));

        assertThat(judge(jev).approved()).isTrue();
        assertThat(jev.calls()).hasSize(2);
    }
}
