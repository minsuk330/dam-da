package com.khack.review.practice.domain;

/**
 * 답변 판정이 문제가 모호하다고 보류됐다({@code HoldReason.questionNeedsRecheck}: 판정 불가·질문 오해). 피드백 요청과 상관없이
 * 그 문제를 한 번 재검사한다(스펙 §6.4.5 보류 처리).
 */
public record QuestionHeldAsAmbiguous(Long userId, Long questionId, Long presentationId) {
}
