package com.khack.review.tools.benchmark;

import com.khack.review.collection.domain.AiVerdict;

/** Benchmark-level verdict: whether the AI confirmed the user's understanding or corrected it (fully or partly). */
public enum AiVerdictGroup {
    confirmed, corrected;

    public boolean matches(AiVerdict verdict) {
        return switch (this) {
            case confirmed -> verdict == AiVerdict.confirmed;
            case corrected -> verdict == AiVerdict.partial || verdict == AiVerdict.corrected;
        };
    }
}
