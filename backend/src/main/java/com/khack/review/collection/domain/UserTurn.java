package com.khack.review.collection.domain;

import org.jspecify.annotations.Nullable;

/** Optional fields are omitted by the model when they hold their default, to keep the tool arguments small. */
public record UserTurn(
        int index,
        String text,
        @Nullable String quotedText,
        Intent intent,
        @Nullable AiVerdict aiVerdict,
        @Nullable String correction) {

    public AiVerdict effectiveVerdict() {
        return aiVerdict == null ? AiVerdict.not_applicable : aiVerdict;
    }
}
