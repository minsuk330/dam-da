package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.ReviewUnitVerdict;
import com.khack.review.collection.domain.Fidelity;
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
 * 복습 단위 1개를 Jev로 검수하고 결과를 해석한다. 신뢰도 기준은 {@link UnitReviewPolicy}가 정한다.
 * 429·529는 짧게 재시도하고, 끝내 실패하면 {@link ReviewUnitVerdict#UNAVAILABLE}로 사용자 확인에 넘긴다.
 */
@Component
public class UnitReviewJudge {

    private static final Logger log = LoggerFactory.getLogger(UnitReviewJudge.class);
    private static final int EVIDENCE_FIT_MAX = UnitReviewQuestions.EVIDENCE_FIT_LEVELS.size() - 1;

    /** 재시도 대기. 테스트는 실제로 기다리지 않는다. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final JevPort jev;
    private final UnitReviewPolicy policy;
    private final Sleeper sleeper;

    @Autowired
    public UnitReviewJudge(JevPort jev, UnitReviewPolicy policy) {
        this(jev, policy, Thread::sleep);
    }

    UnitReviewJudge(JevPort jev, UnitReviewPolicy policy, Sleeper sleeper) {
        this.jev = jev;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public UnitReviewOutcome judge(UnitReviewState state, Fidelity fidelity) {
        JevResult result;
        try {
            result = evaluateWithRetry(state);
        } catch (RuntimeException e) {
            log.warn("복습 단위 검수 실패: {}", e.getMessage());
            return new UnitReviewOutcome(ReviewUnitVerdict.UNAVAILABLE, "Jev 호출 실패: " + e.getMessage());
        }
        try {
            return interpret(result, fidelity);
        } catch (IllegalArgumentException e) {
            return new UnitReviewOutcome(ReviewUnitVerdict.UNAVAILABLE, "Jev 답 해석 실패: " + e.getMessage());
        }
    }

    private JevResult evaluateWithRetry(UnitReviewState state) {
        for (int attempt = 1; ; attempt++) {
            try {
                return jev.evaluate(state, UnitReviewQuestions.questions());
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

    private UnitReviewOutcome interpret(JevResult result, Fidelity fidelity) {
        JevAnswer.Noul worth = result.noul(UnitReviewQuestions.WORTH_REVIEWING);
        JevAnswer.Score fit = result.score(UnitReviewQuestions.EVIDENCE_FIT);
        double minConfidence = policy.minConfidenceFor(fidelity);
        double fitRatio = fit.score() / EVIDENCE_FIT_MAX;
        String detail = "worth_reviewing %.2f, evidence_fit %.2f/%d (신뢰도 %.2f, 기준 %.2f), %s".formatted(
                worth.probability(), fit.score(), EVIDENCE_FIT_MAX, fit.confidence(), minConfidence, result.model());

        if (policy.inHoldBand(worth.probability())) {
            return new UnitReviewOutcome(ReviewUnitVerdict.HELD, "복습 가치 판정이 애매함: " + detail);
        }
        if (fit.confidence() < minConfidence) {
            return new UnitReviewOutcome(ReviewUnitVerdict.HELD, "근거 연결 신뢰도가 기준보다 낮음: " + detail);
        }
        List<String> failures = new ArrayList<>();
        if (worth.probability() < policy.holdBandLow()) {
            failures.add("복습 가치 없음");
        }
        if (fitRatio < policy.minEvidenceFit()) {
            failures.add("근거 연결 부족");
        }
        if (failures.isEmpty()) {
            return new UnitReviewOutcome(ReviewUnitVerdict.APPROVED, detail);
        }
        return new UnitReviewOutcome(ReviewUnitVerdict.REJECTED, String.join(", ", failures) + ": " + detail);
    }
}
