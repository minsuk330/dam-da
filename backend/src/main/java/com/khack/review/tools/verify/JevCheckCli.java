package com.khack.review.tools.verify;

import com.khack.review.common.adapter.out.typesafe.TypeSafeJevAdapter;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.web.client.RestClient;

/**
 * 실제 TypeSafe API로 Jev를 한 번 호출해 결과를 출력한다. 키는 backend/.env의 TYPESAFE_API_KEY.
 * 사용: ./gradlew -q jevCheck -Pargs="<사용자 답변>"  (생략하면 예시 답변)
 */
public final class JevCheckCli {

    public static void main(String[] args) throws IOException {
        String apiKey = loadApiKey();
        if (apiKey.isBlank()) {
            System.err.println("TYPESAFE_API_KEY가 없습니다. backend/.env에 넣으세요.");
            System.exit(1);
        }
        String answer = args.length > 0 ? String.join(" ", args) : "읽기는 스냅샷을 보니까 락을 안 잡는다";

        JevPort jev = new TypeSafeJevAdapter(RestClient.builder(), apiKey, "https://api.typesafe.ai", "jev-latest");
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("correct", "정답 기준을 모두 충족");
        statuses.put("partial", "정답 기준을 일부만 충족");
        statuses.put("misconception", "틀린 개념을 사실로 믿고 있음");
        statuses.put("misunderstood_question", "질문 자체를 다르게 이해함");
        statuses.put("unable_to_judge", null);

        JevResult result = jev.evaluate(Map.of(
                "question", "MySQL InnoDB에서 일반 SELECT는 행에 락을 거는가?",
                "criteria", "일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다. FOR UPDATE/SHARE는 락을 건다.",
                "answer", answer), Map.of(
                "status", JevQuestion.choice("`criteria` 기준으로 `answer`는 어떤 상태인가?", statuses),
                "grounded", JevQuestion.noul("`answer`가 `question`에 대한 답인가?"),
                "clarity", JevQuestion.score("`answer`가 얼마나 명확한가?", List.of("모호", "보통", "명확"))));

        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
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
