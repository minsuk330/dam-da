package com.khack.review.common.config;

import com.khack.review.common.application.TimeTravelClock;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각의 유일한 출처. 시간 이동 데모 모드(`/dev/clock`)가 오프셋을 옮기며, 오프셋이 0이면 실제 시각이다.
 * DB(PostgreSQL·H2)는 마이크로초까지 저장하므로 시각도 마이크로초로 자른다. Windows 시계는 100ns 단위라
 * 자르지 않으면 저장 전후 값이 달라진다.
 */
@Configuration
class ClockConfig {

    @Bean
    TimeTravelClock clock() {
        return new TimeTravelClock(Clock.tick(Clock.systemDefaultZone(), Duration.of(1, ChronoUnit.MICROS)));
    }
}
