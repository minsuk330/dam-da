package com.khack.review.memory.domain;

import com.khack.review.question.domain.QuestionType;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * 등급 변환 정책의 입력 (스펙 §6.4.5). 판정 값은 답변 판정(Jev, 객관식은 코드 채점)이 채운다.
 *
 * @param verdictConfidence  {@code verdict} 신뢰도(0~1). 객관식 코드 채점은 1
 * @param misread            {@code failures}에 `misread`가 있는가
 * @param misreadConfidence  `misread` 신뢰도(0~1). 없으면 0
 * @param guessSuspected     정답이지만 추측이 의심되는가(예: 읽기 어려운 시간 안에 고른 객관식 정답)
 * @param responseTime       답변 제출까지 걸린 시간. 모르면 null이고, 그러면 Easy를 주지 않는다
 */
public record RatingInput(
        AttemptKind attempt,
        AnswerVerdict verdict,
        double verdictConfidence,
        boolean misread,
        double misreadConfidence,
        boolean guessSuspected,
        SelfAssessment selfAssessment,
        QuestionType questionType,
        @Nullable Duration responseTime) {
}
