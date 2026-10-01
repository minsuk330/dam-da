package com.khack.review.collection.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@code turns} are the user turns whose following AI answers explained this point. */
public record KeyPoint(String point, List<Integer> turns, @Nullable FactKind kind) {

    public FactKind effectiveKind() {
        return kind == null ? FactKind.fact : kind;
    }
}
