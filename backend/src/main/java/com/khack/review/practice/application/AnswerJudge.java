package com.khack.review.practice.application;

import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.practice.domain.AnswerJudgment;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 서술형 답변 1개를 Jev로 판정한다 (스펙 §6.4.5). 질문을 한 번에 묶어 부르고, 429·529는 짧게 재시도한다.
 * 결과는 해석만 하고 신뢰도 기준은 적용하지 않는다(등급 변환이 적용). 부를 수 없거나 해석할 수 없으면 판정 실패다.
 */
@Component
public class AnswerJudge {

    private static final Logger log = LoggerFactory.getLogger(AnswerJudge.class);

    /** 재시도 대기. 테스트는 실제로 기다리지 않는다. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** 판정 결과. 실패면 {@code jev}가 null이고 {@code failure}에 이유가 있다. */
    public record Outcome(AnswerJudgment.Jev jev, String failure) {

        static Outcome judged(AnswerJudgment.Jev jev) {
            return new Outcome(jev, null);
        }

        static Outcome failed(String failure) {
            return new Outcome(null, failure);
        }

        public boolean judged() {
            return jev != null;
        }
    }

    private final JevPort jev;
    private final AnswerJudgePolicy policy;
    private final Sleeper sleeper;

    @Autowired
    public AnswerJudge(JevPort jev, AnswerJudgePolicy policy) {
        this(jev, policy, Thread::sleep);
    }

    AnswerJudge(JevPort jev, AnswerJudgePolicy policy, Sleeper sleeper) {
        this.jev = jev;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public Outcome judge(AnswerJudgeState state) {
        JevResult result;
        try {
            result = evaluateWithRetry(state);
        } catch (RuntimeException e) {
            log.warn("답변 판정 실패: {}", e.getMessage());
            return Outcome.failed("Jev 호출 실패: " + e.getMessage());
        }
        try {
            return Outcome.judged(interpret(result, state.userBelief() != null));
        } catch (IllegalArgumentException e) {
            return Outcome.failed("Jev 답 해석 실패: " + e.getMessage());
        }
    }

    private JevResult evaluateWithRetry(AnswerJudgeState state) {
        for (int attempt = 1; ; attempt++) {
            try {
                return jev.evaluate(state, AnswerJudgeQuestions.questions(state.userBelief() != null));
            } catch (JevCallException e) {
                if (!e.retryable() || attempt >= policy.maxAttempts()) {
                    throw e;
                }
                Duration delay = policy.retryDelay().multipliedBy(attempt);
                log.info("Jev {} 응답, {} 뒤 재시도 ({}/{})", e.status(), delay, attempt, policy.maxAttempts());
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Jev 재시도 대기 중 중단됨", interrupted);
                }
            }
        }
    }

    private static AnswerJudgment.Jev interpret(JevResult result, boolean withUserBelief) {
        JevAnswer.Choice verdict = result.choice(AnswerJudgeQuestions.VERDICT);
        return new AnswerJudgment.Jev(verdict(verdict.choice()), verdict.confidence(),
                result.noul(AnswerJudgeQuestions.OMISSION).probability(),
                result.noul(AnswerJudgeQuestions.CONTRADICTION).probability(),
                result.noul(AnswerJudgeQuestions.MISREAD).probability(),
                withUserBelief ? result.noul(AnswerJudgeQuestions.REPEATS_USER_BELIEF).probability() : null,
                result.noul(AnswerJudgeQuestions.OFF_TARGET_ERROR).probability(),
                result.model());
    }

    private static AnswerVerdict verdict(String choice) {
        return switch (choice) {
            case AnswerJudgeQuestions.MET -> AnswerVerdict.MET;
            case AnswerJudgeQuestions.NOT_MET -> AnswerVerdict.NOT_MET;
            case AnswerJudgeQuestions.UNABLE_TO_JUDGE -> AnswerVerdict.UNABLE_TO_JUDGE;
            default -> throw new IllegalArgumentException("알 수 없는 verdict: " + choice);
        };
    }
}
