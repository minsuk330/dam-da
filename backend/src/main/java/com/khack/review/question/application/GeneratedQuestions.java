package com.khack.review.question.application;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** LLM 응답 형태. 필드 뜻은 {@link QuestionPrompt#SYSTEM}의 [출력 필드]를 따른다. */
public record GeneratedQuestions(@Nullable List<Item> questions) {

    public record Item(
            @Nullable String targetId,
            @Nullable String question,
            @Nullable List<String> choices,
            @Nullable Integer correctChoiceIndex,
            @Nullable List<String> answerCriteria,
            @Nullable String modelAnswer,
            @Nullable String hint,
            @Nullable String explanation) {
    }
}
