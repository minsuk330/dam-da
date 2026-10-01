package com.khack.review.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TimeTravelClockTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    TimeTravelClock clock = new TimeTravelClock(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startsAtRealTime() {
        assertThat(clock.instant()).isEqualTo(NOW);
        assertThat(clock.offset()).isZero();
    }

    @Test
    void travelAccumulatesAndResetReturnsToRealTime() {
        clock.travel(Duration.ofDays(7));
        clock.travel(Duration.ofHours(3));

        assertThat(clock.instant()).isEqualTo(NOW.plus(Duration.ofDays(7).plusHours(3)));

        clock.reset();
        assertThat(clock.instant()).isEqualTo(NOW);
    }

    @Test
    void cannotTravelBackwards() {
        assertThatThrownBy(() -> clock.travel(Duration.ofDays(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zonedCopySharesTheOffset() {
        Clock seoul = clock.withZone(ZoneId.of("Asia/Seoul"));

        clock.travel(Duration.ofDays(1));

        assertThat(seoul.instant()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(seoul.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
