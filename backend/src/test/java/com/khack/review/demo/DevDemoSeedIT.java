package com.khack.review.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import com.khack.review.common.json.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/**
 * 로그인 계정에 시연 기록 넣기: ID로 전환 → 과거 시각으로 저장 → 검수 대기 → 확인·목표 → 문제 준비 → 학습 시작 →
 * 항목 조회 → 합성 기록 붙이기. LLM·Jev는 데모 시나리오와 같은 가짜다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DemoScenarioIT.Fakes.class)
class DevDemoSeedIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    AppUserRepository users;

    @Autowired
    Clock clock;

    @AfterEach
    void backToDemoUser() {
        currentUser.reset();
    }

    @Test
    void seedsASessionAtAPastTimeOnAnExistingAccountAndAttachesHistory() throws Exception {
        assertThat(exchange("POST", "/dev/demo-seed/sessions", seedBody(clock.instant())).statusCode()).isEqualTo(400);

        AppUser account = users.save(new AppUser("google:seed-it", clock.instant()));
        assertThat(exchange("POST", "/dev/current-user", "{\"id\": 999999999}").statusCode()).isEqualTo(400);
        assertThat(send("POST", "/dev/current-user", "{\"id\": %d}".formatted(account.getId())).get("id").asLong())
                .isEqualTo(account.getId());

        Instant conversationAt = clock.instant().minus(Duration.ofDays(40)).truncatedTo(ChronoUnit.SECONDS);
        JsonNode seeded = send("POST", "/dev/demo-seed/sessions", seedBody(conversationAt));
        long sessionId = seeded.get("sessionId").asLong();
        assertThat(Instant.parse(seeded.get("createdAt").asString())).isEqualTo(conversationAt);

        await(sessionId, "AWAITING_CONFIRMATION");
        JsonNode confirmed = send("POST", "/dev/demo-seed/sessions/%d/confirm".formatted(sessionId), "{}");
        assertThat(confirmed.get("goals").get(0).asString()).isEqualTo("CORRECT_MISCONCEPTION");
        await(sessionId, "QUESTIONS_READY");
        assertThat(send("POST", "/dev/demo-seed/sessions/%d/start".formatted(sessionId), null).get("status").asString())
                .isEqualTo("IN_PROGRESS");

        JsonNode items = send("GET", "/dev/demo-seed/items", null);
        assertThat(items.size()).isEqualTo(4);
        assertThat(Instant.parse(items.get(0).get("conversationAt").asString())).isEqualTo(conversationAt);
        long itemId = items.get(0).get("memoryItemId").asLong();

        String attach = """
                {"links": [{"card_id": 1, "memory_item_id": %d}],
                 "reviews": [{"card_id": 1, "rating": 3, "review_datetime": "%s", "review_duration": 4000},
                             {"card_id": 1, "rating": 3, "review_datetime": "%s", "review_duration": 4000},
                             {"card_id": 2, "rating": 1, "review_datetime": "%s", "review_duration": 4000}]}"""
                .formatted(itemId, conversationAt.plus(Duration.ofHours(2)), conversationAt.plus(Duration.ofDays(3)), conversationAt);
        JsonNode attached = send("POST", "/dev/synthetic-history/attach", attach);
        assertThat(attached.get("items").asInt()).isEqualTo(1);
        assertThat(send("GET", "/api/memory/model", null).get("progress").get("gradedReviews").asLong()).isEqualTo(3);

        send("POST", "/dev/synthetic-history/attach", attach);
        assertThat(send("GET", "/api/memory/model", null).get("progress").get("gradedReviews").asLong()).isEqualTo(3);
    }

    private static String seedBody(Instant conversationAt) {
        return Json.MAPPER.writeValueAsString(Map.of("session", DemoScenarioIT.CONVERSATION, "conversationAt", conversationAt));
    }

    private void await(long sessionId, String status) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (status.equals(send("GET", "/dev/demo-seed/sessions/" + sessionId, null).get("status").asString())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("학습 세션 %d가 %s가 되지 않음".formatted(sessionId, status));
    }

    private JsonNode send(String method, String path, String json) throws Exception {
        HttpResponse<String> response = exchange(method, path, json);
        assertThat(response.statusCode()).as(method + " " + path + " → " + response.body()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    private HttpResponse<String> exchange(String method, String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
