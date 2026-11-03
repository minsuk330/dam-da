package com.khack.review.engagement.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.json.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevUsageMetricsIT {

    @Value("${local.server.port}")
    int port;

    @Test
    void metricsAreServedOnTheDevToolsPath() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/dev/metrics")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = Json.MAPPER.readTree(response.body());
        assertThat(body.has("usersWithConversation")).isTrue();
        assertThat(body.get("day7").get("days").asInt()).isEqualTo(7);
    }
}
