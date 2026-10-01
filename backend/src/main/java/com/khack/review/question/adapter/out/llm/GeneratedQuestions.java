package com.khack.review.question.adapter.out.llm;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** LLM 응답 형태. 필드 뜻은 {@link QuestionPrompt#SYSTEM}의 [출력 필드]를 따른다. */
record GeneratedQuestions(@Nullable List<Item> questions) {

    record Item(
            @Nullable String targetId,
            @Nullable String stem,
            @Nullable List<String> choices,
            @Nullable Integer correctChoice,
            @Nullable List<String> answerCriteria,
            @Nullable String modelAnswer,
            @Nullable String hint,
            @Nullable String explanation) {
    }
}
