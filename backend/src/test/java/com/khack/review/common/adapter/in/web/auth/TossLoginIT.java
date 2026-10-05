package com.khack.review.common.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeTossLoginPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 토스 인앱 로그인(#149): 미니앱 appLogin() 인가 코드 → 앱 토큰. 같은 토스 사용자는 같은 계정이다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.auth.required=true")
class TossLoginIT {

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        FakeTossLoginPort fakeTossLoginPort() {
            return new FakeTossLoginPort();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeTossLoginPort toss;

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String code) throws Exception {
        return send("POST", "/api/auth/toss", null, "{\"authorizationCode\":\"" + code + "\",\"referrer\":\"SANDBOX\"}");
    }

    private static String accessToken(String body) {
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private static String userId(String body) {
        return body.replaceAll(".*\"user\":\\{\"id\":(\\d+).*", "$1");
    }

    @Test
    void tossCodeBecomesAnAppTokenForTheSameAccount() throws Exception {
        toss.willAccept("first", "443731104").willAccept("again", "443731104").willAccept("other", "999");

        HttpResponse<String> first = login("first");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"name\":\"토스 사용자\"");

        HttpResponse<String> me = send("GET", "/api/me", accessToken(first.body()), null);
        assertThat(me.statusCode()).isEqualTo(200);

        HttpResponse<String> again = login("again");
        assertThat(userId(again.body())).isEqualTo(userId(first.body()));
        assertThat(userId(login("other").body())).isNotEqualTo(userId(first.body()));
    }

    @Test
    void rejectedOrMissingCodeIs401() throws Exception {
        assertThat(login("unknown").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/toss", null, "{\"authorizationCode\":\"\",\"referrer\":\"DEFAULT\"}").statusCode())
                .isEqualTo(401);
    }
}
