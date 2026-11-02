package com.khack.review.common.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {

    final TimeTravelClock clock = new TimeTravelClock(Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC));
    final RequestRateLimiter limiter = new RequestRateLimiter(clock);
    final Duration minute = Duration.ofMinutes(1);

    @Test
    void allowsUpToTheLimitWithinTheWindow() {
        assertThat(limiter.tryAcquire("ai:1", 2, minute)).isTrue();
        assertThat(limiter.tryAcquire("ai:1", 2, minute)).isTrue();
        assertThat(limiter.tryAcquire("ai:1", 2, minute)).isFalse();
    }

    @Test
    void countsEachKeySeparately() {
        assertThat(limiter.tryAcquire("ai:1", 1, minute)).isTrue();
        assertThat(limiter.tryAcquire("ai:2", 1, minute)).isTrue();
        assertThat(limiter.tryAcquire("ai:1", 1, minute)).isFalse();
    }

    @Test
    void allowsAgainOnceTheOldRequestsLeaveTheWindow() {
        limiter.tryAcquire("ai:1", 1, minute);
        clock.travel(Duration.ofSeconds(59));
        assertThat(limiter.tryAcquire("ai:1", 1, minute)).isFalse();

        clock.travel(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("ai:1", 1, minute)).isTrue();
    }
}
