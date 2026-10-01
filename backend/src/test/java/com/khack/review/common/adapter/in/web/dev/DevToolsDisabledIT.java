package com.khack.review.common.adapter.in.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.dev-tools.enabled=false")
class DevToolsDisabledIT {

    @Value("${local.server.port}")
    int port;

    @Test
    void devToolsAreHiddenWhenDisabled() throws Exception {
        for (String path : new String[] {"/dev/clock", "/dev/sessions", "/dev/sessions.md"}) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path).isEqualTo(404);
        }
    }
}
