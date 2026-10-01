package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.practice.application.AnswerJudge;
import com.khack.review.practice.application.AnswerJudgeState;
import com.khack.review.practice.domain.AnswerJudgment;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** 실제 Jev로 서술형 답변을 판정한다. 맞는 답은 met, 대화에서 믿었던 틀린 내용을 반복한 답은 not_met이어야 한다. */
@Tag("live")
@SpringBootTest
class LiveAnswerJudgeIT {

    static final String QUESTION = "다음 주장에서 틀린 곳을 찾아 바르게 고치세요: \"SELECT ... FOR UPDATE는 스캔을 모두 마친 뒤 한꺼번에 락을 건다.\"";
    static final List<String> CRITERIA = List.of("스캔하면서 읽는 레코드마다 즉시 배타 락을 건다고 고친다");
    static final String MODEL_ANSWER = "스캔을 마친 뒤가 아니라, 스캔하면서 읽는 레코드마다 즉시 배타 락을 건다.";
    static final String BELIEF = "스캔을 모두 마친 뒤 한꺼번에 락을 건다";
    static final String CORRECTION = "스캔하면서 읽는 레코드마다 즉시 잠근다";

    @Autowired
    Environment env;

    @Autowired
    AnswerJudge judge;

    @BeforeEach
    void keys() {
        LiveKeys.require(env);
    }

    private AnswerJudge.Outcome judge(String answer) {
        AnswerJudge.Outcome outcome = judge.judge(new AnswerJudgeState(QUESTION, "ERROR_FINDING", CRITERIA, MODEL_ANSWER, answer,
                BELIEF, BELIEF, CORRECTION));
        System.out.printf("[live] \"%s\" → %s%n", answer, outcome.judged() ? outcome.jev() : outcome.failure());
        assertThat(outcome.judged()).as(outcome.failure()).isTrue();
        return outcome;
    }

    @Test
    void correctAnswerIsMet() {
        assertThat(judge("한꺼번에가 아니라 스캔하면서 읽는 레코드마다 바로 배타 락을 건다.").jev().verdict()).isEqualTo(AnswerVerdict.MET);
    }

    @Test
    void repeatingTheOldBeliefIsNotMet() {
        AnswerJudge.Outcome outcome = judge("틀린 곳 없다. 스캔을 다 끝내고 나서 락을 한 번에 건다.");
        assertThat(outcome.jev().verdict()).isEqualTo(AnswerVerdict.NOT_MET);
    }

    @Test
    void repeatingTheOldBeliefIsAWrongAnswerNotAMisreading() {
        AnswerJudgment.Jev jev = judge("틀린 곳 없다. 스캔을 다 끝내고 나서 락을 한 번에 건다.").jev();

        assertThat(jev.misread().misread()).as("질문에 답하려다 틀린 답을 질문 오독으로 보면 보류되어 Again이 되지 않는다").isFalse();
        assertThat(jev.contradiction()).isGreaterThanOrEqualTo(0.7);
        assertThat(jev.repeatsUserBelief()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    void answeringADifferentQuestionIsNotMet() {
        AnswerJudgment.Jev jev = judge("FOR UPDATE는 SELECT 문 끝에 붙이는 SQL 구문이다.").jev();

        assertThat(jev.verdict()).isNotEqualTo(AnswerVerdict.MET);
    }

    @Test
    void offTopicAnswerIsJudged() {
        judge("인덱스를 만들면 된다.");
    }
}
