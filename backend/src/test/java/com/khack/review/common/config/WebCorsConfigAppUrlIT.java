package com.khack.review.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** CORS 목록에 앱 주소가 없어도(토스 출처만 넣은 배포 설정) 앱 주소 출처는 허용된다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "review.cors.allowed-origins=https://damda-ai.apps.tossmini.com",
        "review.auth.app-url=https://app.example/"
})
class WebCorsConfigAppUrlIT {

    @Value("${local.server.port}")
    int port;

    private HttpResponse<String> get(String path, String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Origin", origin)
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void appUrlOriginIsAllowedEvenWhenNotListed() throws Exception {
        HttpResponse<String> response = get("/api/conversations", "https://app.example");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("https://app.example");
    }

    @Test
    void listedOriginIsStillAllowed() throws Exception {
        assertThat(get("/api/conversations", "https://damda-ai.apps.tossmini.com").statusCode()).isEqualTo(200);
    }

    @Test
    void otherOriginIsRejected() throws Exception {
        assertThat(get("/api/conversations", "https://evil.example").statusCode()).isEqualTo(403);
    }
}
