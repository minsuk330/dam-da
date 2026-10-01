package com.khack.review.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @Async} 이벤트 처리(예: 복습 단위 검수). Spring Boot 기본 실행기를 쓴다. */
@Configuration
@EnableAsync
class AsyncConfig {
}
