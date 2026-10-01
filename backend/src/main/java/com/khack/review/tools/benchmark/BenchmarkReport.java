package com.khack.review.tools.benchmark;

import com.khack.review.tools.verify.TurnComparison;
import java.util.List;

public record BenchmarkReport(
        TurnComparison turns,
        Score verdicts,
        Score intents,
        FactScore facts,
        List<String> forbiddenHits) {

    public record Score(int matched, int expected, List<String> misses) {
    }

    public record FactScore(List<String> found, List<String> missing) {
    }
}
