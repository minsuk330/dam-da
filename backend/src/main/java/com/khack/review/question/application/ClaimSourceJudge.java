package com.khack.review.question.application;

import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 정답 기준의 사실 주장을 외부 출처와 대조한다(#178). 일치·모순은 신뢰도가 기준 이상일 때만 확정하고,
 * 그 밖(출처 없음, 낮은 신뢰도, 호출·해석 실패)은 확인 불가다. 판정만 하고 문제 출제나 기억 상태는 바꾸지 않는다.
 */
@Component
public class ClaimSourceJudge {

    private static final Logger log = LoggerFactory.getLogger(ClaimSourceJudge.class);

    public enum Verdict {
        SUPPORTED, CONTRADICTED, UNVERIFIABLE
    }

    /** {@code confidence}는 Jev choice 신뢰도. Jev를 부르지 않았으면 0이다. */
    public record ClaimCheck(Verdict verdict, double confidence, String reason) {
    }

    private final JevPort jev;
    private final double minConfidence;

    public ClaimSourceJudge(JevPort jev, @Value("${review.question.fact-check.min-confidence}") double minConfidence) {
        this.jev = jev;
        this.minConfidence = minConfidence;
    }

    public ClaimCheck judge(ClaimSourceState state) {
        if (state.sources().isEmpty()) {
            return new ClaimCheck(Verdict.UNVERIFIABLE, 0, "출처 없음");
        }
        JevAnswer.Choice answer;
        try {
            JevResult result = jev.evaluate(state, ClaimSourceQuestions.questions());
            answer = result.choice(ClaimSourceQuestions.SUPPORT);
        } catch (RuntimeException e) {
            log.warn("출처 대조 실패: {}", e.getMessage());
            return new ClaimCheck(Verdict.UNVERIFIABLE, 0, "Jev 호출·해석 실패: " + e.getMessage());
        }
        if (answer.confidence() < minConfidence) {
            return new ClaimCheck(Verdict.UNVERIFIABLE, answer.confidence(), "신뢰도 낮음(" + answer.choice() + ")");
        }
        return switch (answer.choice()) {
            case ClaimSourceQuestions.SUPPORTED -> new ClaimCheck(Verdict.SUPPORTED, answer.confidence(), "출처와 일치");
            case ClaimSourceQuestions.CONTRADICTED -> new ClaimCheck(Verdict.CONTRADICTED, answer.confidence(), "출처와 모순");
            default -> new ClaimCheck(Verdict.UNVERIFIABLE, answer.confidence(), "출처가 다루지 않음");
        };
    }
}
