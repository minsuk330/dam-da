package com.khack.review.analysis.application;

import com.khack.review.analysis.application.FieldTaxonomy.Field;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.common.application.port.out.JevResult;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 세션의 학습 분야를 Jev 2단계 choice로 고른다 (스펙 §7.10). 1단계는 대분류, 2단계는 그 대분류의 소분류다.
 * 신뢰도가 기준보다 낮거나 호출·해석이 실패하면 자동으로 정하지 않고, 1단계면 미분류, 2단계면 그 대분류의 기타로 둔다.
 * 429·529는 짧게 재시도한다.
 */
@Component
public class SessionFieldClassifier {

    private static final Logger log = LoggerFactory.getLogger(SessionFieldClassifier.class);

    /** 재시도 대기. 테스트는 실제로 기다리지 않는다. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final JevPort jev;
    private final FieldTaxonomy taxonomy;
    private final SessionFieldPolicy policy;
    private final Sleeper sleeper;

    @Autowired
    public SessionFieldClassifier(JevPort jev, FieldTaxonomy taxonomy, SessionFieldPolicy policy) {
        this(jev, taxonomy, policy, Thread::sleep);
    }

    SessionFieldClassifier(JevPort jev, FieldTaxonomy taxonomy, SessionFieldPolicy policy, Sleeper sleeper) {
        this.jev = jev;
        this.taxonomy = taxonomy;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public SessionFieldOutcome classify(SessionFieldState state) {
        Step first = ask(state, SessionFieldQuestions.field(taxonomy), SessionFieldQuestions.FIELD);
        Optional<Field> field = first.answer() == null ? Optional.empty() : taxonomy.field(first.answer().choice());
        if (field.isEmpty() || !confident(first.answer())) {
            return new SessionFieldOutcome(FieldTaxonomy.UNCLASSIFIED, "대분류 " + first.describe(policy.minConfidence()));
        }
        Step second = ask(state.withField(field.get().label()), SessionFieldQuestions.subfield(field.get()), SessionFieldQuestions.SUBFIELD);
        String reason = "대분류 " + first.describe(policy.minConfidence()) + ", 소분류 " + second.describe(policy.minConfidence());
        boolean inField = second.answer() != null
                && taxonomy.fieldOf(second.answer().choice()).filter(field.get()::equals).isPresent();
        if (!inField || !confident(second.answer())) {
            return new SessionFieldOutcome(FieldTaxonomy.fallbackFor(field.get().code()), reason);
        }
        return new SessionFieldOutcome(second.answer().choice(), reason);
    }

    private boolean confident(JevAnswer.Choice answer) {
        return answer.confidence() >= policy.minConfidence();
    }

    private Step ask(SessionFieldState state, Map<String, JevQuestion> questions, String name) {
        try {
            var result = evaluateWithRetry(state, questions);
            return new Step(result.choice(name), result.model(), null);
        } catch (RuntimeException e) {
            log.warn("학습 분야 판정 실패({}): {}", name, e.getMessage());
            return new Step(null, null, e.getMessage());
        }
    }

    private JevResult evaluateWithRetry(SessionFieldState state,
            Map<String, JevQuestion> questions) {
        for (int attempt = 1; ; attempt++) {
            try {
                return jev.evaluate(state, questions);
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

    private record Step(JevAnswer.@Nullable Choice answer, @Nullable String model, @Nullable String failure) {

        String describe(double minConfidence) {
            if (answer == null) {
                return "실패(" + failure + ")";
            }
            return "%s (신뢰도 %.2f, 기준 %.2f, %s)".formatted(answer.choice(), answer.confidence(), minConfidence, model);
        }
    }
}
