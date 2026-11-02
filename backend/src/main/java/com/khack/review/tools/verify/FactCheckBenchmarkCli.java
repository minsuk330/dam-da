package com.khack.review.tools.verify;

import com.khack.review.common.adapter.out.typesafe.TypeSafeJevAdapter;
import com.khack.review.common.json.Json;
import com.khack.review.question.application.ClaimSourceJudge;
import com.khack.review.question.application.ClaimSourceJudge.ClaimCheck;
import com.khack.review.question.application.ClaimSourceJudge.Verdict;
import com.khack.review.question.application.ClaimSourceState;
import com.khack.review.question.application.port.out.SourceSearchPort.SourceExcerpt;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.web.client.RestClient;

/**
 * 출처 대조 고정 벤치마크(#178). 일부러 틀린 주장을 섞은 사례를 실제 Jev로 판정해 틀린 주장 검출률과 맞는 주장 오차단율을 낸다.
 * 키는 backend/.env의 TYPESAFE_API_KEY. 사례당 Jev 1회 호출(비용).
 * 사용: ./gradlew -q factCheckBenchmark -Pargs="[fixtures/benchmarks/fact-check.json] [--min-confidence 0.6] [--out build/fact-check.json]"
 */
public final class FactCheckBenchmarkCli {

    record BenchmarkCase(String id, String expected, String claim, List<SourceExcerpt> sources) {
    }

    record BenchmarkFile(String description, List<BenchmarkCase> cases) {
    }

    record CaseResult(String id, String expected, String verdict, double confidence, String reason) {
    }

    record Summary(int cases, double minConfidence, int contradictedCases, int contradictionsCaught, int supportedCases,
            int supportedBlocked, int notAddressedCases, int notAddressedConfirmed, int matches, Map<String, Integer> verdicts,
            List<CaseResult> results) {
    }

    public static void main(String[] args) throws IOException {
        List<String> options = List.of(args);
        String file = options.stream().filter(a -> a.endsWith(".json") && !isOptionValue(options, a)).findFirst()
                .orElse("fixtures/benchmarks/fact-check.json");
        double minConfidence = optionValue(options, "--min-confidence").map(Double::parseDouble).orElse(0.6);
        String apiKey = loadApiKey();
        if (apiKey.isBlank()) {
            System.err.println("TYPESAFE_API_KEY가 없습니다. backend/.env에 넣으세요.");
            System.exit(1);
        }
        BenchmarkFile benchmark = Json.MAPPER.readValue(Files.readString(Path.of(file), StandardCharsets.UTF_8), BenchmarkFile.class);
        ClaimSourceJudge judge = new ClaimSourceJudge(
                new TypeSafeJevAdapter(RestClient.builder(), apiKey, "https://api.typesafe.ai", "jev-latest"), minConfidence);

        List<CaseResult> results = new ArrayList<>();
        Map<String, Integer> verdicts = new LinkedHashMap<>();
        int contradicted = 0, caught = 0, supported = 0, blocked = 0, notAddressed = 0, confirmed = 0, matches = 0;
        for (BenchmarkCase c : benchmark.cases()) {
            ClaimCheck check = judge.judge(new ClaimSourceState(c.claim(), c.sources()));
            results.add(new CaseResult(c.id(), c.expected(), check.verdict().name(), check.confidence(), check.reason()));
            verdicts.merge(check.verdict().name(), 1, Integer::sum);
            Verdict v = check.verdict();
            switch (c.expected()) {
                case "contradicted" -> {
                    contradicted++;
                    if (v == Verdict.CONTRADICTED) {
                        caught++;
                    }
                }
                case "supported" -> {
                    supported++;
                    if (v == Verdict.CONTRADICTED) {
                        blocked++;
                    }
                }
                default -> {
                    notAddressed++;
                    if (v != Verdict.UNVERIFIABLE) {
                        confirmed++;
                    }
                }
            }
            if (expectedVerdict(c.expected()) == v) {
                matches++;
            }
            System.out.printf("%-30s expected=%-13s got=%-12s conf=%.2f%n", c.id(), c.expected(), v, check.confidence());
        }
        Summary summary = new Summary(results.size(), minConfidence, contradicted, caught, supported, blocked, notAddressed,
                confirmed, matches, verdicts, results);
        System.out.printf("contradictions caught %d/%d, supported wrongly flagged %d/%d, not-addressed given a verdict %d/%d, exact %d/%d%n",
                caught, contradicted, blocked, supported, confirmed, notAddressed, matches, results.size());
        OpenAiCli.print(summary, options);
    }

    private static Verdict expectedVerdict(String expected) {
        return switch (expected) {
            case "supported" -> Verdict.SUPPORTED;
            case "contradicted" -> Verdict.CONTRADICTED;
            default -> Verdict.UNVERIFIABLE;
        };
    }

    private static boolean isOptionValue(List<String> options, String value) {
        int i = options.indexOf(value);
        return i > 0 && options.get(i - 1).startsWith("--");
    }

    private static java.util.Optional<String> optionValue(List<String> options, String name) {
        int i = options.indexOf(name);
        return i >= 0 && i + 1 < options.size() ? java.util.Optional.of(options.get(i + 1)) : java.util.Optional.empty();
    }

    private static String loadApiKey() throws IOException {
        Path env = Path.of(".env");
        if (Files.exists(env)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(env)) {
                properties.load(reader);
            }
            String key = properties.getProperty("TYPESAFE_API_KEY", "");
            if (!key.isBlank()) {
                return key.trim();
            }
        }
        return System.getenv().getOrDefault("TYPESAFE_API_KEY", "");
    }
}
