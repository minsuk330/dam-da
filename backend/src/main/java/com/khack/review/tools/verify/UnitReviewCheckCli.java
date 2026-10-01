package com.khack.review.tools.verify;

import com.khack.review.analysis.application.UnitReviewJudge;
import com.khack.review.analysis.application.UnitReviewOutcome;
import com.khack.review.analysis.application.UnitReviewPolicy;
import com.khack.review.analysis.application.UnitReviewQuestions;
import com.khack.review.analysis.application.UnitReviewState;
import com.khack.review.analysis.application.UnitReviewState.EvidenceTurn;
import com.khack.review.analysis.application.UnitReviewState.Item;
import com.khack.review.collection.domain.Fidelity;
import com.khack.review.common.adapter.out.typesafe.TypeSafeJevAdapter;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.web.client.RestClient;

/**
 * 복습 단위 검수 질문({@link UnitReviewQuestions})으로 실제 Jev를 호출해 원 답과 해석 결과를 출력한다.
 * 질문이나 기준을 바꿀 때 실제 확률·신뢰도 분포를 보는 용도다. 키는 backend/.env의 TYPESAFE_API_KEY.
 * 사용: ./gradlew -q unitReviewCheck
 */
public final class UnitReviewCheckCli {

    /** application.yml의 review.analysis 기본값과 같게 유지한다. */
    private static final UnitReviewPolicy POLICY = new UnitReviewPolicy(0.5, 0.6, 0.3, 0.7, 0.5, 3, Duration.ofSeconds(1));

    public static void main(String[] args) throws IOException {
        String apiKey = loadApiKey();
        if (apiKey.isBlank()) {
            System.err.println("TYPESAFE_API_KEY가 없습니다. backend/.env에 넣으세요.");
            System.exit(1);
        }
        JevPort adapter = new TypeSafeJevAdapter(RestClient.builder(), apiKey, "https://api.typesafe.ai", "jev-latest");
        Map<String, JevResult> raw = new LinkedHashMap<>();
        JevPort recording = (state, questions) -> {
            JevResult result = adapter.evaluate(state, questions);
            raw.put(((UnitReviewState) state).unitTitle(), result);
            return result;
        };
        UnitReviewJudge judge = new UnitReviewJudge(recording, POLICY);

        for (Map.Entry<String, UnitReviewState> sample : samples().entrySet()) {
            UnitReviewOutcome outcome = judge.judge(sample.getValue(), Fidelity.model_transcribed);
            System.out.println("== " + sample.getKey());
            JevResult result = raw.get(sample.getValue().unitTitle());
            if (result != null) {
                System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result.answers()));
            }
            System.out.println("→ " + outcome.verdict() + " | " + outcome.reason());
            System.out.println();
        }
    }

    private static Map<String, UnitReviewState> samples() {
        Map<String, UnitReviewState> samples = new LinkedHashMap<>();
        samples.put("기대: 통과 (핵심 사실 + 헷갈린 지점, 근거 일치)", new UnitReviewState("MySQL InnoDB", "MVCC와 일반 SELECT",
                List.of(new Item("fact", "일반 SELECT는 MVCC 스냅샷을 읽으므로 행 락을 걸지 않는다", List.of(1, 2)),
                        new Item("confusion", "일반 SELECT도 공유 락을 건다", List.of(2))),
                List.of(new EvidenceTurn(1, "InnoDB에서 SELECT 하면 락이 걸려?", "info_request", null, null),
                        new EvidenceTurn(2, "그럼 SELECT도 S 락 거는 거 맞지?", "understanding_check", "corrected",
                                "일반 SELECT는 락 없이 스냅샷을 읽는다. FOR SHARE를 붙여야 S 락을 건다"))));
        samples.put("기대: 실패 (복습 가치 없음)", new UnitReviewState("MySQL InnoDB", "대화 마무리",
                List.of(new Item("fact", "사용자는 오늘 공부를 여기까지 하기로 했다", List.of(3))),
                List.of(new EvidenceTurn(3, "오케이 오늘은 여기까지 할게 고마워", "restatement", null, null))));
        samples.put("기대: 실패 (근거 연결 부족)", new UnitReviewState("MySQL InnoDB", "갭 락",
                List.of(new Item("fact", "REPEATABLE READ에서 범위 조건 잠금 읽기는 갭 락으로 팬텀을 막는다", List.of(1))),
                List.of(new EvidenceTurn(1, "인덱스 컬럼 순서가 왜 중요해?", "info_request", null, null))));
        return samples;
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
