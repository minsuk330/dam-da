package com.khack.review.question.application;

import com.khack.review.question.application.port.out.QuestionRequest;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 품질 검사 Jev 상태. 필드 이름은 {@link QuestionQualityQuestions}의 질문 문장이 가리킨다. */
public record QuestionQualityState(
        String item,
        String itemKind,
        @Nullable String correction,
        List<QuestionRequest.EvidenceTurn> evidence,
        String type,
        String question,
        List<String> choices,
        @Nullable Integer correctChoice,
        List<String> answerCriteria,
        List<String> existing) {
}
