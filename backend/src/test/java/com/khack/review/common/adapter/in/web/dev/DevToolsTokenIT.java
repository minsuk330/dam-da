package com.khack.review.common.adapter.in.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.dev-tools.token=demo-secret")
class DevToolsTokenIT {

    static final String REMOTE = "160.79.106.167";

    @Value("${local.server.port}")
    int port;

    private int status(String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/dev/clock"))
                .header("X-Forwarded-For", REMOTE);
        if (token != null) {
            request.header("X-Dev-Token", token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void remoteRequestWithTheTokenIsAllowed() throws Exception {
        assertThat(status("demo-secret")).isEqualTo(200);
    }

    @Test
    void remoteRequestWithoutOrWithWrongTokenIsHidden() throws Exception {
        assertThat(status(null)).isEqualTo(404);
        assertThat(status("wrong")).isEqualTo(404);
        assertThat(status("")).isEqualTo(404);
    }
}
