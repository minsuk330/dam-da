package com.khack.review.common.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.application.port.out.FakeConversationExtractor;
import com.khack.review.common.json.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/** 요청 한도(#184). 한도를 낮춘 별도 컨텍스트에서 넘으면 429와 안내 문구가 오는지 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "review.limits.conversations-per-day=1",
        "review.limits.auth-requests-per-minute=2"})
@Import(RateLimitIT.Fakes.class)
class RateLimitIT {

    @TestConfiguration
    static class Fakes {

        @Bean
        FakeConversationExtractor fakeConversationExtractor() {
            return new FakeConversationExtractor();
        }
    }

    @Value("${local.server.port}")
    int port;

    private HttpResponse<String> post(String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void conversationsBeyondTheDailyLimitAreRejected() throws Exception {
        assertThat(post("/api/conversations/paste", "{\"text\":\"표준오차가 뭐야?\"}").statusCode()).isEqualTo(201);

        HttpResponse<String> second = post("/api/conversations/paste", "{\"text\":\"표준편차는?\"}");

        assertThat(second.statusCode()).isEqualTo(429);
        JsonNode body = Json.MAPPER.readTree(second.body());
        assertThat(body.get("code").asString()).isEqualTo("rate_limited");
        assertThat(body.get("message").asString()).contains("오늘은 대화를 1개까지");
    }

    @Test
    void loginRequestsBeyondThePerMinuteLimitAreRejected() throws Exception {
        String invalid = "{\"code\":\"x\"}";
        assertThat(post("/api/auth/token", invalid).statusCode()).isNotEqualTo(429);
        assertThat(post("/api/auth/token", invalid).statusCode()).isNotEqualTo(429);

        assertThat(post("/api/auth/token", invalid).statusCode()).isEqualTo(429);
    }
}
