package com.khack.review.tools.verify;

import java.util.List;

public record TurnComparison(
        int expectedCount,
        int actualCount,
        int exact,
        int normalized,
        List<Missing> missing,
        List<Extra> extra,
        boolean orderPreserved,
        Verdict verdict) {

    public record Missing(int index, String expected, String closest) {
    }

    public record Extra(int index, String actual) {
    }

    public enum Verdict {
        IDENTICAL, WHITESPACE_ONLY, DIVERGED
    }
}
