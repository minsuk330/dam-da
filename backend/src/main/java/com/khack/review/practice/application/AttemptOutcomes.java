package com.khack.review.practice.application;

import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.memory.domain.HoldReason;
import com.khack.review.memory.domain.RatingDecision;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.RatingPolicy;
import com.khack.review.practice.domain.AnswerJudgment;
import com.khack.review.practice.domain.AttemptOutcome;
import com.khack.review.practice.domain.JudgmentStatus;
import com.khack.review.practice.domain.PracticeAttempt;
import org.springframework.stereotype.Component;

/**
 * 판정 → 시도 결과 (스펙 §6.4.5). 제출 응답과 단계적 피드백이 같은 결과를 쓴다. 판정 실패·신뢰도 미달은 UNCERTAIN(등급 변환이 보류한
 * 경우와 같다), 판정 불가·질문 오해는 문제가 모호한 것이라 QUESTION_AMBIGUOUS, 나머지는 verdict대로다. 신뢰도 기준은 등급 변환 정책에
 * 맡기며, 도움 후 재시도는 등급 변환을 거치지 않으므로 첫 무도움 시도로 가정해 같은 정책에 물어본다. 추측 의심 보류는 맞힌 것이므로 CORRECT다.
 */
@Component
public class AttemptOutcomes {

    private final RatingPolicy ratingPolicy;

    public AttemptOutcomes(RatingPolicy ratingPolicy) {
        this.ratingPolicy = ratingPolicy;
    }

    public AttemptOutcome of(PracticeAttempt attempt, AnswerJudgment judgment) {
        if (judgment.getStatus() == JudgmentStatus.FAILED) {
            return AttemptOutcome.UNCERTAIN;
        }
        RatingInput input = new RatingInput(AttemptKind.FIRST_UNASSISTED, judgment.getVerdict(), judgment.getVerdictConfidence(),
                judgment.isMisread(), judgment.getMisreadConfidence() == null ? 0 : judgment.getMisreadConfidence(), false,
                attempt.getSelfAssessment(), attempt.getQuestionType(), null, judgment.isEvidenceTranscribed());
        if (ratingPolicy.decide(input) instanceof RatingDecision.Held held) {
            return held.reason() == HoldReason.LOW_CONFIDENCE ? AttemptOutcome.UNCERTAIN : AttemptOutcome.QUESTION_AMBIGUOUS;
        }
        return judgment.getVerdict() == AnswerVerdict.MET ? AttemptOutcome.CORRECT : AttemptOutcome.WRONG;
    }
}
