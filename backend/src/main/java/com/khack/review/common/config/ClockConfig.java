package com.khack.review.common.config;

import com.khack.review.common.application.TimeTravelClock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각의 유일한 출처. 시간 이동 데모 모드(`/dev/clock`)가 오프셋을 옮기며, 오프셋이 0이면 실제 시각이다.
 */
@Configuration
class ClockConfig {

    @Bean
    TimeTravelClock clock() {
        return new TimeTravelClock(Clock.systemDefaultZone());
    }
}
