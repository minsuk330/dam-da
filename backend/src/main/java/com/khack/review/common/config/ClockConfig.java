package com.khack.review.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각의 유일한 출처. 시간 이동 데모 모드는 이 빈을 교체해 구현한다.
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
