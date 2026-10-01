package com.khack.review.tools.benchmark;

import com.khack.review.collection.domain.SavedSession;
import com.khack.review.common.json.Json;
import com.khack.review.tools.verify.SessionSource;
import java.nio.file.Path;
import java.util.Optional;

public final class BenchmarkCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: ./gradlew -q benchmark -Pargs=\"<benchmark.json> [sessionId]\"");
            System.exit(1);
        }
        Benchmark benchmark = Benchmark.load(Path.of(args[0]));
        Optional<SavedSession> session = SessionSource.find(args, 1);
        if (session.isEmpty()) {
            System.err.println("No saved connector session found.");
            System.exit(1);
        }
        BenchmarkReport report = BenchmarkScorer.score(benchmark, session.get());
        System.out.println("session " + session.get().id());
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
