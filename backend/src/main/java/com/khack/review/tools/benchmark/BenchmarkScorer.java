package com.khack.review.tools.benchmark;

import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.tools.benchmark.Benchmark.ExpectedTurn;
import com.khack.review.tools.benchmark.Benchmark.FactGroup;
import com.khack.review.tools.benchmark.Benchmark.Forbidden;
import com.khack.review.tools.benchmark.BenchmarkReport.FactScore;
import com.khack.review.tools.benchmark.BenchmarkReport.Score;
import com.khack.review.tools.verify.TurnComparator;
import com.khack.review.tools.verify.TurnComparison;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Keyword-level scoring; approximate by design, so results are reviewed by hand alongside. */
public final class BenchmarkScorer {

    private BenchmarkScorer() {
    }

    public static BenchmarkReport score(Benchmark benchmark, SavedSession session) {
        List<UserTurn> learning = session.userTurns().stream()
                .filter(t -> t.intent() != Intent.meta)
                .sorted(Comparator.comparingInt(UserTurn::index))
                .toList();
        TurnComparison turns = TurnComparator.compare(
                benchmark.turns().stream().map(ExpectedTurn::text).toList(),
                learning.stream().map(UserTurn::text).toList());

        List<KeyPoint> facts = session.reviewUnits() == null ? List.of()
                : session.reviewUnits().stream().filter(u -> u.keyPoints() != null).flatMap(u -> u.keyPoints().stream()).toList();
        return new BenchmarkReport(turns, verdicts(benchmark, learning), intents(benchmark, learning),
                facts(benchmark, facts), forbidden(benchmark, facts));
    }

    private static Optional<UserTurn> find(List<UserTurn> learning, String text) {
        String target = TurnComparator.normalize(text);
        return learning.stream().filter(t -> TurnComparator.normalize(t.text()).equals(target)).findFirst();
    }

    private static Score verdicts(Benchmark benchmark, List<UserTurn> learning) {
        int matched = 0;
        int expected = 0;
        List<String> misses = new ArrayList<>();
        for (int i = 0; i < benchmark.turns().size(); i++) {
            ExpectedTurn e = benchmark.turns().get(i);
            if (e.verdict() == null) {
                continue;
            }
            expected++;
            Optional<UserTurn> actual = find(learning, e.text());
            if (actual.isEmpty()) {
                misses.add("%d번 발화 누락 (기대 %s)".formatted(i + 1, e.verdict()));
            } else if (e.verdict().matches(actual.get().effectiveVerdict())) {
                matched++;
            } else {
                misses.add("%d번 발화: 기대 %s, 실제 %s".formatted(i + 1, e.verdict(), actual.get().effectiveVerdict()));
            }
        }
        return new Score(matched, expected, List.copyOf(misses));
    }

    private static Score intents(Benchmark benchmark, List<UserTurn> learning) {
        int matched = 0;
        int expected = 0;
        List<String> misses = new ArrayList<>();
        for (int i = 0; i < benchmark.turns().size(); i++) {
            ExpectedTurn e = benchmark.turns().get(i);
            if (e.intent() == null) {
                continue;
            }
            expected++;
            Optional<UserTurn> actual = find(learning, e.text());
            if (actual.isEmpty()) {
                misses.add("%d번 발화 누락 (기대 %s)".formatted(i + 1, e.intent()));
            } else if (actual.get().intent() == e.intent()) {
                matched++;
            } else {
                misses.add("%d번 발화: 기대 %s, 실제 %s".formatted(i + 1, e.intent(), actual.get().intent()));
            }
        }
        return new Score(matched, expected, List.copyOf(misses));
    }

    private static FactScore facts(Benchmark benchmark, List<KeyPoint> facts) {
        List<String> texts = facts.stream().map(k -> k.point().toLowerCase(Locale.ROOT)).toList();
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (FactGroup group : benchmark.facts()) {
            boolean hit = texts.stream().anyMatch(text -> group.allOf().stream()
                    .allMatch(any -> any.stream().anyMatch(k -> text.contains(k.toLowerCase(Locale.ROOT)))));
            (hit ? found : missing).add(group.id());
        }
        return new FactScore(List.copyOf(found), List.copyOf(missing));
    }

    private static List<String> forbidden(Benchmark benchmark, List<KeyPoint> facts) {
        List<String> hits = new ArrayList<>();
        for (Forbidden f : benchmark.forbidden()) {
            Pattern pattern = Pattern.compile(f.regex());
            facts.stream()
                    .filter(k -> k.effectiveKind() != FactKind.warning && pattern.matcher(k.point()).find())
                    .forEach(k -> hits.add(f.id() + ": " + k.point()));
        }
        return List.copyOf(hits);
    }
}
