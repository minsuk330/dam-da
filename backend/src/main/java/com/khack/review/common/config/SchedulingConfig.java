package com.khack.review.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 작업(예: 매일 학습 알림 확인). */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
