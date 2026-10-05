package com.khack.review.common.adapter.in.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.TimeTravelClock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevClockIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    Clock clock;

    @AfterEach
    void resetClock() {
        ((TimeTravelClock) clock).reset();
    }

    private HttpResponse<String> send(String method, String path, String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void travelMovesTheInjectedClockAndResetReturnsIt() throws Exception {
        Instant before = clock.instant();

        HttpResponse<String> response = send("POST", "/dev/clock/travel?days=7", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"offset\":\"PT168H\"");
        assertThat(Duration.between(before, clock.instant())).isGreaterThanOrEqualTo(Duration.ofDays(7));

        assertThat(send("POST", "/dev/clock/reset", null).body()).contains("\"offset\":\"PT0S\"");
        assertThat(Duration.between(before, clock.instant())).isLessThan(Duration.ofDays(1));
    }

    @Test
    void rejectsZeroOrBackwardTravel() throws Exception {
        assertThat(send("POST", "/dev/clock/travel?days=-1", null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/dev/clock/travel", null).statusCode()).isEqualTo(400);
    }

    @Test
    void tunneledRequestsAreHidden() throws Exception {
        assertThat(send("GET", "/dev/clock", "160.79.106.167").statusCode()).isEqualTo(404);
        assertThat(send("POST", "/dev/clock/travel?days=7", "160.79.106.167").statusCode()).isEqualTo(404);
        assertThat(((TimeTravelClock) clock).offset()).isZero();
    }
}
