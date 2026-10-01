package com.khack.review.tools.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.tools.benchmark.Benchmark.ExpectedTurn;
import com.khack.review.tools.benchmark.Benchmark.FactGroup;
import com.khack.review.tools.benchmark.Benchmark.Forbidden;
import com.khack.review.tools.verify.TurnComparison;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkScorerTest {

    private static final Benchmark BENCH = new Benchmark("t",
            List.of(
                    new ExpectedTurn("범위만 잠그면 되지 않나?", AiVerdictGroup.corrected, null),
                    new ExpectedTurn("스캔하고 락 거는 거 아니야?", AiVerdictGroup.confirmed, Intent.understanding_check),
                    new ExpectedTurn("간단하게 설명해줘", null, Intent.rephrase_request)),
            List.of(new FactGroup("explain", List.of(List.of("EXPLAIN"), List.of("ALL"))),
                    new FactGroup("supremum", List.of(List.of("supremum")))),
            List.of(new Forbidden("rc-record-only", "(READ COMMITTED|RC)[^\\n]{0,30}record lock만")));

    private static UserTurn turn(int i, String text, Intent intent, AiVerdict verdict) {
        return new UserTurn(i, text, null, intent, verdict, null);
    }

    private static SavedSession session(List<UserTurn> turns, KeyPoint... points) {
        return new SavedSession("s", "2026-09-27T00:00:00Z", "connector", "model_transcribed", turns,
                List.of(new ReviewUnit("u", List.of(points), null)), null, List.of(), null, null);
    }

    @Test
    void scoresTurnsVerdictsIntentsFactsAndForbiddenPatterns() {
        SavedSession s = session(
                List.of(turn(1, "범위만 잠그면 되지 않나?", Intent.understanding_check, AiVerdict.partial),
                        turn(2, "간단하게 설명해줘", Intent.info_request, null),
                        turn(3, "복습에 넣어줘", Intent.meta, null)),
                new KeyPoint("EXPLAIN에서 type ALL이면 전부 잠긴다", List.of(1), FactKind.practice),
                new KeyPoint("READ COMMITTED에서는 record lock만 걸린다", List.of(1), null));

        BenchmarkReport r = BenchmarkScorer.score(BENCH, s);

        assertThat(r.turns().expectedCount()).isEqualTo(3);
        assertThat(r.turns().actualCount()).isEqualTo(2);
        assertThat(r.turns().missing()).extracting(TurnComparison.Missing::index).containsExactly(2);
        assertThat(r.verdicts().matched()).isEqualTo(1);
        assertThat(r.verdicts().expected()).isEqualTo(2);
        assertThat(r.verdicts().misses()).anyMatch(m -> m.contains("2") && m.contains("누락"));
        assertThat(r.intents().matched()).isZero();
        assertThat(r.intents().misses()).anyMatch(m -> m.contains("rephrase_request") && m.contains("info_request"));
        assertThat(r.facts().found()).containsExactly("explain");
        assertThat(r.facts().missing()).containsExactly("supremum");
        assertThat(r.forbiddenHits()).anyMatch(h -> h.startsWith("rc-record-only"));
    }

    @Test
    void forbiddenPatternIsAllowedInsideWarnings() {
        SavedSession s = session(List.of(turn(1, "범위만 잠그면 되지 않나?", Intent.understanding_check, AiVerdict.corrected)),
                new KeyPoint("RC = record lock만으로 외우면 안 된다", List.of(1), FactKind.warning));
        assertThat(BenchmarkScorer.score(BENCH, s).forbiddenHits()).isEmpty();
    }

    @Test
    void factGroupMustBeSatisfiedWithinASingleFact() {
        Benchmark bench = new Benchmark("t", List.of(new ExpectedTurn("q", null, null)),
                List.of(new FactGroup("explain", List.of(List.of("EXPLAIN"), List.of("ALL")))), List.of());
        SavedSession s = session(List.of(turn(1, "q", Intent.info_request, null)),
                new KeyPoint("EXPLAIN으로 실행 계획을 본다", List.of(1), null),
                new KeyPoint("ALL 이라는 단어가 다른 사실에 있다", List.of(1), null));
        assertThat(BenchmarkScorer.score(bench, s).facts().missing()).containsExactly("explain");
    }
}
