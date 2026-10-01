package com.khack.review.practice.application;

import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.practice.domain.FeedbackAction;
import com.khack.review.practice.domain.FeedbackRules;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 허용된 행동 중 다음 행동을 Jev로 고른다 (스펙 §6.2). 신뢰도 기준은 {@link FeedbackPolicy}가 정한다.
 * 선택지가 하나면 호출하지 않는다. 호출 실패(429·529는 짧게 재시도)·신뢰도 부족·허용 밖 답이면 규칙의 기본 행동을 쓴다.
 * 다음 행동은 학습 흐름만 바꾸고 기억 상태는 바꾸지 않으므로, 판정이 불확실할 때 기본 행동으로 물러서도 안전하다.
 */
@Component
public class NextActionJudge {

    private static final Logger log = LoggerFactory.getLogger(NextActionJudge.class);

    /** 누가 정했는가. */
    public enum DecidedBy {
        /** 선택지가 하나라 규칙이 정했다. */
        RULE,
        /** Jev가 고른 행동. */
        JEV,
        /** Jev를 쓰지 못해(실패·신뢰도 부족·허용 밖 답) 규칙의 기본 행동으로 대신했다. */
        FALLBACK
    }

    public record Choice(FeedbackAction action, DecidedBy decidedBy, String detail) {
    }

    /** 재시도 대기. 테스트는 실제로 기다리지 않는다. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final JevPort jev;
    private final FeedbackPolicy policy;
    private final Sleeper sleeper;

    @Autowired
    public NextActionJudge(JevPort jev, FeedbackPolicy policy) {
        this(jev, policy, Thread::sleep);
    }

    NextActionJudge(JevPort jev, FeedbackPolicy policy, Sleeper sleeper) {
        this.jev = jev;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public Choice choose(NextActionState state, FeedbackRules.Plan plan) {
        if (!plan.needsJudgment()) {
            return new Choice(plan.fallback(), DecidedBy.RULE, "선택지 하나");
        }
        JevResult result;
        try {
            result = evaluateWithRetry(state, plan);
        } catch (RuntimeException e) {
            log.warn("다음 행동 선택 실패: {}", e.getMessage());
            return new Choice(plan.fallback(), DecidedBy.FALLBACK, "Jev 호출 실패: " + e.getMessage());
        }
        try {
            JevAnswer.Choice answer = result.choice(NextActionQuestions.NEXT_ACTION);
            String detail = "%s (신뢰도 %.2f, 기준 %.2f), %s".formatted(answer.choice(), answer.confidence(),
                    policy.minConfidence(), result.model());
            if (answer.confidence() < policy.minConfidence()) {
                return new Choice(plan.fallback(), DecidedBy.FALLBACK, "신뢰도가 기준보다 낮음: " + detail);
            }
            for (FeedbackAction action : plan.allowed()) {
                if (NextActionQuestions.optionName(action).equals(answer.choice().toLowerCase(Locale.ROOT))) {
                    return new Choice(action, DecidedBy.JEV, detail);
                }
            }
            return new Choice(plan.fallback(), DecidedBy.FALLBACK, "허용되지 않은 행동: " + detail);
        } catch (IllegalArgumentException e) {
            return new Choice(plan.fallback(), DecidedBy.FALLBACK, "Jev 답 해석 실패: " + e.getMessage());
        }
    }

    private JevResult evaluateWithRetry(NextActionState state, FeedbackRules.Plan plan) {
        for (int attempt = 1; ; attempt++) {
            try {
                return jev.evaluate(state, NextActionQuestions.questions(plan.allowed()));
            } catch (JevCallException e) {
                if (!e.retryable() || attempt >= policy.maxAttempts()) {
                    throw e;
                }
                Duration delay = policy.retryDelay().multipliedBy(attempt);
                log.info("Jev {} 응답, {} 뒤 재시도 ({}/{})", e.status(), delay, attempt, policy.maxAttempts());
                pause(delay);
            }
        }
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Jev 재시도 대기 중 중단됨", e);
        }
    }
}
