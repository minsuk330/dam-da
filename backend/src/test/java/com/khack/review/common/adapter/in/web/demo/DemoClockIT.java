package com.khack.review.common.adapter.in.web.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.AuthTokenService;
import com.khack.review.common.application.SocialSignInService;
import com.khack.review.common.application.SocialSignInService.SocialProfile;
import com.khack.review.common.application.TimeTravelClock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 앱 시연 도구의 서버 시계: 로그인한 사용자는 개발 도구 토큰 없이 원격(프록시 경유)에서도 옮기고 되돌린다.
 * 로그인하지 않았으면 401이고, 개발 도구 경로(/dev/clock)는 여전히 토큰이 필요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"review.auth.required=true", "review.demo-clock.enabled=true"})
class DemoClockIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    AuthTokenService tokens;

    @Autowired
    SocialSignInService signIn;

    @Autowired
    TimeTravelClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** 배포 서버처럼 프록시를 거친 원격 요청으로 보낸다. */
    private HttpResponse<String> send(String method, String path, @Nullable String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .header("X-Forwarded-For", "160.79.106.167");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void signedInUsersMoveTheClockWithoutTheDevToken() throws Exception {
        String token = tokens.issue(signIn.signIn(new SocialProfile("google", "demo-clock-it", "시연자", null)).getId()).accessToken();
        Instant before = clock.instant();

        HttpResponse<String> moved = send("POST", "/api/demo/clock/travel?days=3", token);

        assertThat(moved.statusCode()).as(moved.body()).isEqualTo(200);
        assertThat(moved.body()).contains("\"offset\":\"PT72H\"");
        assertThat(Duration.between(before, clock.instant())).isGreaterThanOrEqualTo(Duration.ofDays(3));
        assertThat(send("GET", "/api/demo/clock", token).body()).contains("\"offset\":\"PT72H\"");
        assertThat(send("POST", "/api/demo/clock/travel?days=-1", token).statusCode()).isEqualTo(400);

        assertThat(send("POST", "/api/demo/clock/reset", token).body()).contains("\"offset\":\"PT0S\"");
        assertThat(clock.offset()).isZero();
    }

    @Test
    void anonymousRequestsAndTheDevPathStayClosed() throws Exception {
        assertThat(send("POST", "/api/demo/clock/travel?days=3", null).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/dev/clock/travel?days=3", null).statusCode()).isEqualTo(404);
        assertThat(clock.offset()).isZero();
    }
}
