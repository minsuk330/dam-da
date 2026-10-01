package com.khack.review.tools.benchmark;

import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionStore;
import com.khack.review.common.json.Json;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;

public final class BenchmarkCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: ./gradlew -q benchmark -Pargs=\"<benchmark.json> [sessionId]\"");
            System.exit(1);
        }
        Benchmark benchmark = Benchmark.load(Path.of(args[0]));
        SessionStore store = new SessionStore(Path.of(System.getProperty("review.sessions-file", "data/sessions.jsonl")), Clock.systemUTC());
        Optional<SavedSession> session = args.length > 1
                ? store.list().stream().filter(s -> s.id().equals(args[1])).findFirst()
                : store.latest();
        if (session.isEmpty()) {
            System.err.println("No saved connector session found.");
            System.exit(1);
        }
        BenchmarkReport report = BenchmarkScorer.score(benchmark, session.get());
        System.out.println("session " + session.get().id());
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
