package com.khack.review.question.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.question.application.ClaimSourceJudge.Verdict;
import com.khack.review.question.application.port.out.SourceSearchPort.SourceExcerpt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaimSourceJudgeTest {

    static final ClaimSourceState STATE = new ClaimSourceState("InnoDB의 일반 SELECT는 행 잠금을 걸지 않는다.",
            List.of(new SourceExcerpt("https://example.com/innodb", "InnoDB 읽기", "일반 SELECT는 스냅샷을 읽고 잠금을 걸지 않는다.")));

    final FakeJevPort jev = new FakeJevPort();
    final ClaimSourceJudge judge = new ClaimSourceJudge(jev, 0.6);

    static JevResult answer(String choice, double confidence) {
        return new JevResult("fake", Map.of(ClaimSourceQuestions.SUPPORT,
                new JevAnswer.Choice(choice, Map.of(choice, confidence), confidence)));
    }

    @Test
    void confidentAnswersBecomeSupportedOrContradicted() {
        jev.willReturn(answer(ClaimSourceQuestions.SUPPORTED, 0.9));
        assertThat(judge.judge(STATE).verdict()).isEqualTo(Verdict.SUPPORTED);

        jev.willReturn(answer(ClaimSourceQuestions.CONTRADICTED, 0.8));
        assertThat(judge.judge(STATE).verdict()).isEqualTo(Verdict.CONTRADICTED);
    }

    @Test
    void lowConfidenceOrUnaddressedClaimsAreUnverifiable() {
        jev.willReturn(answer(ClaimSourceQuestions.CONTRADICTED, 0.4));
        assertThat(judge.judge(STATE).verdict()).isEqualTo(Verdict.UNVERIFIABLE);

        jev.willReturn(answer(ClaimSourceQuestions.NOT_ADDRESSED, 0.95));
        assertThat(judge.judge(STATE).verdict()).isEqualTo(Verdict.UNVERIFIABLE);
    }

    @Test
    void noSourcesSkipJevAndJevFailuresAreUnverifiable() {
        assertThat(judge.judge(new ClaimSourceState("주장", List.of())).verdict()).isEqualTo(Verdict.UNVERIFIABLE);
        assertThat(jev.calls()).isEmpty();

        jev.willFail(new JevCallException(529, "overloaded", null));
        assertThat(judge.judge(STATE).verdict()).isEqualTo(Verdict.UNVERIFIABLE);
    }
}
