package com.khack.review.question.application;

import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 문제 후보 1개를 Jev로 품질 검사한다. 근거성·명확성·중복을 모두 통과해야 승인한다. 확신할 수 없거나 Jev를 부를 수 없으면
 * 승인하지 않는다(규칙 3: 품질 검사 전에는 보이지 않는다). 429·529는 짧게 재시도한다.
 */
@Component
public class QuestionQualityJudge {

    private static final Logger log = LoggerFactory.getLogger(QuestionQualityJudge.class);
    private static final int CLARITY_MAX = QuestionQualityQuestions.CLARITY_LEVELS.size() - 1;

    /** 재시도 대기. 테스트는 실제로 기다리지 않는다. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final JevPort jev;
    private final QuestionQualityPolicy policy;
    private final Sleeper sleeper;

    @Autowired
    public QuestionQualityJudge(JevPort jev, QuestionQualityPolicy policy) {
        this(jev, policy, Thread::sleep);
    }

    QuestionQualityJudge(JevPort jev, QuestionQualityPolicy policy, Sleeper sleeper) {
        this.jev = jev;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public QualityOutcome judge(QuestionQualityState state) {
        JevResult result;
        try {
            result = evaluateWithRetry(state);
        } catch (RuntimeException e) {
            log.warn("문제 품질 검사 실패: {}", e.getMessage());
            return new QualityOutcome(false, "Jev 호출 실패: " + e.getMessage());
        }
        try {
            return interpret(result);
        } catch (IllegalArgumentException e) {
            return new QualityOutcome(false, "Jev 답 해석 실패: " + e.getMessage());
        }
    }

    private JevResult evaluateWithRetry(QuestionQualityState state) {
        for (int attempt = 1; ; attempt++) {
            try {
                return jev.evaluate(state, QuestionQualityQuestions.questions());
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

    private QualityOutcome interpret(JevResult result) {
        JevAnswer.Noul grounded = result.noul(QuestionQualityQuestions.GROUNDED);
        JevAnswer.Score clarity = result.score(QuestionQualityQuestions.CLARITY);
        JevAnswer.Noul duplicate = result.noul(QuestionQualityQuestions.DUPLICATE);
        double clarityRatio = clarity.score() / CLARITY_MAX;
        String detail = "grounded %.2f, clarity %.2f/%d (신뢰도 %.2f), duplicate %.2f, %s".formatted(
                grounded.probability(), clarity.score(), CLARITY_MAX, clarity.confidence(), duplicate.probability(), result.model());

        List<String> failures = new ArrayList<>();
        if (grounded.probability() < policy.minGrounded()) {
            failures.add("근거 부족");
        }
        if (clarity.confidence() < policy.minConfidence()) {
            failures.add("명확성 판정 신뢰도 낮음");
        } else if (clarityRatio < policy.minClarity()) {
            failures.add("모호함");
        }
        if (duplicate.probability() >= policy.maxDuplicate()) {
            failures.add("기존 문제와 중복");
        }
        return failures.isEmpty()
                ? new QualityOutcome(true, detail)
                : new QualityOutcome(false, String.join(", ", failures) + ": " + detail);
    }
}
