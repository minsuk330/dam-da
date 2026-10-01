package com.khack.review.tools.benchmark;

import com.khack.review.collection.domain.Intent;
import com.khack.review.common.json.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Ground truth for one conversation: every learning user turn in order, the verdict and intent expected on
 * selected turns, key facts as keyword groups, and patterns that must not appear as plain facts.
 */
public record Benchmark(String name, List<ExpectedTurn> turns, List<FactGroup> facts, List<Forbidden> forbidden) {

    /** {@code verdict} and {@code intent} are null when the turn is not scored on them. */
    public record ExpectedTurn(String text, AiVerdictGroup verdict, Intent intent) {
    }

    /** Found when every inner list has at least one keyword present in the review units' key facts. */
    public record FactGroup(String id, List<List<String>> allOf) {
    }

    public record Forbidden(String id, String regex) {
    }

    public static Benchmark load(Path path) {
        try {
            return Json.MAPPER.readValue(Files.readString(path), Benchmark.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
