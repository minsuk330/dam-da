package com.khack.review.question.application;

import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.question.application.port.out.SourceSearchPort.SourceExcerpt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 정답 기준의 사실 주장을 외부 출처와 대조한다(#178). 출처마다 Jev를 따로 불러 판정하고, 신뢰도가 기준 이상인 판정만 모아 정한다.
 * <ul>
 *   <li>일치하는 출처와 모순되는 출처가 함께 있으면 확인 불가(출처끼리 다름)</li>
 *   <li>모순만 있으면 모순, 일치만 있으면 일치</li>
 *   <li>그 밖(출처 없음, 모두 낮은 신뢰도·다루지 않음, 호출·해석 실패)은 확인 불가</li>
 * </ul>
 * 판정만 하고 문제 출제나 기억 상태는 바꾸지 않는다.
 */
@Component
public class ClaimSourceJudge {

    private static final Logger log = LoggerFactory.getLogger(ClaimSourceJudge.class);

    public enum Verdict {
        SUPPORTED, CONTRADICTED, UNVERIFIABLE
    }

    /** {@code confidence}는 결론을 낸 출처 판정 중 가장 높은 Jev choice 신뢰도. 결론이 없으면 0이다. */
    public record ClaimCheck(Verdict verdict, double confidence, String reason) {
    }

    /** 출처 하나에 대한 Jev 상태. */
    public record SourceState(String claim, SourceExcerpt source) {
    }

    private final JevPort jev;
    private final double minConfidence;

    public ClaimSourceJudge(JevPort jev, @Value("${review.question.fact-check.min-confidence}") double minConfidence) {
        this.jev = jev;
        this.minConfidence = minConfidence;
    }

    public ClaimCheck judge(ClaimSourceState state) {
        int count = state.sources().size();
        if (count == 0) {
            return new ClaimCheck(Verdict.UNVERIFIABLE, 0, "출처 없음");
        }
        int supported = 0;
        int contradicted = 0;
        double supportedConfidence = 0;
        double contradictedConfidence = 0;
        try {
            for (SourceExcerpt source : state.sources()) {
                JevResult result = jev.evaluate(new SourceState(state.claim(), source), ClaimSourceQuestions.questions());
                JevAnswer.Choice answer = result.choice(ClaimSourceQuestions.SUPPORT);
                if (answer.confidence() < minConfidence) {
                    continue;
                }
                if (ClaimSourceQuestions.SUPPORTED.equals(answer.choice())) {
                    supported++;
                    supportedConfidence = Math.max(supportedConfidence, answer.confidence());
                } else if (ClaimSourceQuestions.CONTRADICTED.equals(answer.choice())) {
                    contradicted++;
                    contradictedConfidence = Math.max(contradictedConfidence, answer.confidence());
                }
            }
        } catch (RuntimeException e) {
            log.warn("출처 대조 실패: {}", e.getMessage());
            return new ClaimCheck(Verdict.UNVERIFIABLE, 0, "Jev 호출·해석 실패: " + e.getMessage());
        }
        if (supported > 0 && contradicted > 0) {
            return new ClaimCheck(Verdict.UNVERIFIABLE, 0, "출처끼리 다름(일치 %d, 모순 %d)".formatted(supported, contradicted));
        }
        if (contradicted > 0) {
            return new ClaimCheck(Verdict.CONTRADICTED, contradictedConfidence, "출처와 모순(%d/%d)".formatted(contradicted, count));
        }
        if (supported > 0) {
            return new ClaimCheck(Verdict.SUPPORTED, supportedConfidence, "출처와 일치(%d/%d)".formatted(supported, count));
        }
        return new ClaimCheck(Verdict.UNVERIFIABLE, 0, "출처가 다루지 않거나 신뢰도 낮음");
    }
}
