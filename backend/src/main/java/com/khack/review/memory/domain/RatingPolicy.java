package com.khack.review.memory.domain;

import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import java.time.Duration;
import java.util.Map;

/**
 * 첫 무도움 시도를 FSRS 등급 또는 보류로 바꾼다 (스펙 §6.4.5 변환표). Jev는 답변 내용만 판정하고, 등급은 이 정책이 정한다.
 * 위에서부터 처음 맞는 행을 적용한다.
 *
 * <ol>
 *   <li>`verdict` 신뢰도가 기준 미만, 또는 `unable_to_judge` → 보류</li>
 *   <li>`misread`가 기준 이상의 신뢰도로 확인됨 → 보류</li>
 *   <li>정답이지만 추측 의심이 있고 자기평가로 확인되지 않음 → 보류</li>
 *   <li>`not_met`(`misread` 신뢰도 부족 포함), 또는 "떠올리지 못하고 추측했음" → Again</li>
 *   <li>`met` + "힘들게 떠올렸거나 확신이 약함" → Hard</li>
 *   <li>`met` + 객관식이 아님 + "쉽게 떠올렸음" + 응답 시간이 기준을 크게 넘지 않음 → Easy</li>
 *   <li>`met`, 나머지 → Good</li>
 * </ol>
 *
 * @param minConfidence    행 1·2의 신뢰도 기준
 * @param minConfidenceTranscribed 판정에 쓴 대화 근거가 모델이 옮겨 적은 것(`model_transcribed`)일 때의 행 1·2 기준. 원문 근거보다 높게 잡는다(스펙 §7.3)
 * @param referenceTimes   문제 유형별 기준 응답 시간. 없는 유형은 Easy를 주지 않는다
 * @param easyMaxTimeRatio 응답 시간이 기준 시간의 이 배수 이하일 때만 Easy
 */
public record RatingPolicy(double minConfidence, double minConfidenceTranscribed, Map<QuestionType, Duration> referenceTimes,
        double easyMaxTimeRatio) {

    /**
     * 변환표나 해석을 바꾸면 올린다. 풀이 기록에 함께 남겨 정책별 영향을 비교한다.
     * 2: 모델이 옮겨 적은 근거에 별도 기준(#68). 3: `misread`를 신뢰도가 있는 선택형 판정으로 읽고 기준을 0.80/0.85로 올린다.
     */
    public static final int VERSION = 3;

    public RatingPolicy {
        if (minConfidenceTranscribed < minConfidence) {
            throw new IllegalArgumentException("옮겨 적은 근거의 신뢰도 기준은 원문 근거의 기준보다 낮을 수 없습니다.");
        }
        referenceTimes = Map.copyOf(referenceTimes);
    }

    /** 출처별 기준을 따로 두지 않은 정책(두 기준이 같다). */
    public RatingPolicy(double minConfidence, Map<QuestionType, Duration> referenceTimes, double easyMaxTimeRatio) {
        this(minConfidence, minConfidence, referenceTimes, easyMaxTimeRatio);
    }

    /** 행 1·2에 적용하는 신뢰도 기준. */
    public double minConfidenceFor(boolean evidenceTranscribed) {
        return evidenceTranscribed ? minConfidenceTranscribed : minConfidence;
    }

    public RatingDecision decide(RatingInput input) {
        if (input.attempt() == AttemptKind.ASSISTED_RETRY) {
            return new RatingDecision.NotEvaluated(VERSION);
        }
        if (input.verdict() == AnswerVerdict.UNABLE_TO_JUDGE) {
            return held(HoldReason.UNABLE_TO_JUDGE, 1);
        }
        double minConfidence = minConfidenceFor(input.evidenceTranscribed());
        if (input.verdictConfidence() < minConfidence) {
            return held(HoldReason.LOW_CONFIDENCE, 1);
        }
        if (input.misread() && input.misreadConfidence() >= minConfidence) {
            return held(HoldReason.MISREAD, 2);
        }
        boolean met = input.verdict() == AnswerVerdict.MET;
        if (met && input.guessSuspected() && input.selfAssessment() != SelfAssessment.GUESSED) {
            return held(HoldReason.GUESS_UNCONFIRMED, 3);
        }
        if (!met || input.selfAssessment() == SelfAssessment.GUESSED) {
            return rated(Rating.AGAIN, 4);
        }
        if (input.selfAssessment() == SelfAssessment.RECALLED_WITH_EFFORT) {
            return rated(Rating.HARD, 5);
        }
        if (input.questionType() != QuestionType.MULTIPLE_CHOICE
                && input.selfAssessment() == SelfAssessment.RECALLED_EASILY
                && withinReferenceTime(input)) {
            return rated(Rating.EASY, 6);
        }
        return rated(Rating.GOOD, 7);
    }

    /** 응답 시간은 Easy를 막는 보조 조건으로만 쓴다. 시간만으로 Easy를 주지 않는다. */
    private boolean withinReferenceTime(RatingInput input) {
        Duration reference = referenceTimes.get(input.questionType());
        if (reference == null || input.responseTime() == null) {
            return false;
        }
        return input.responseTime().toMillis() <= reference.toMillis() * easyMaxTimeRatio;
    }

    private static RatingDecision held(HoldReason reason, int row) {
        return new RatingDecision.Held(reason, row, VERSION);
    }

    private static RatingDecision rated(Rating rating, int row) {
        return new RatingDecision.Rated(rating, row, VERSION);
    }
}
