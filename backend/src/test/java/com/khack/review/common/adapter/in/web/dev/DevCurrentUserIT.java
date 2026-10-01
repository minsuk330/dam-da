package com.khack.review.common.adapter.in.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.CurrentUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** 합성 사용자로 전환 → 합성 기록 → 기억 모델 확인 → 기본 데모 사용자로 복귀 (#71). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevCurrentUserIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    CurrentUser currentUser;

    @AfterEach
    void backToDemoUser() {
        currentUser.reset();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void syntheticHistoryGoesOnlyToASwitchedUserAndResetReturnsToTheDemoUser() throws Exception {
        Long demo = currentUser.id();
        String history = """
                [{"card_id": 1, "rating": 3, "review_datetime": "2026-06-01T09:00:00Z", "review_duration": 4000},
                 {"card_id": 1, "rating": 3, "review_datetime": "2026-06-04T09:00:00Z", "review_duration": 4000}]""";

        assertThat(send("POST", "/dev/synthetic-history", history).statusCode()).isEqualTo(400);

        HttpResponse<String> switched = send("POST", "/dev/current-user", "{\"name\": \"합성 사용자 IT\"}");
        assertThat(switched.statusCode()).isEqualTo(200);
        assertThat(switched.body()).contains("\"demoUser\":false");
        assertThat(currentUser.id()).isNotEqualTo(demo);

        HttpResponse<String> imported = send("POST", "/dev/synthetic-history", history);
        assertThat(imported.statusCode()).isEqualTo(200);
        assertThat(imported.body()).contains("\"items\":1", "\"reviews\":2");
        assertThat(send("GET", "/api/memory/model", null).body()).contains("\"gradedReviews\":2", "\"status\":\"DEFAULT\"");

        HttpResponse<String> reset = send("POST", "/dev/current-user/reset", null);
        assertThat(reset.body()).contains("\"demoUser\":true");
        assertThat(currentUser.id()).isEqualTo(demo);
    }
}
