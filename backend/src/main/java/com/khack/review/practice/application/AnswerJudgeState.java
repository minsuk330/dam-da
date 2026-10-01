package com.khack.review.practice.application;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 답변 판정 Jev 상태. 필드 이름은 {@link AnswerJudgeQuestions}의 질문 문장이 가리킨다.
 *
 * @param userBelief 헷갈린 지점 항목이면 대화에서 사용자가 믿었던 내용
 * @param correction 헷갈린 지점 항목이면 대화 속 AI 교정
 */
public record AnswerJudgeState(
        String question,
        String type,
        List<String> answerCriteria,
        String modelAnswer,
        String answer,
        String item,
        @Nullable String userBelief,
        @Nullable String correction) {
}
