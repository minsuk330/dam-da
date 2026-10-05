package com.khack.review.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * `/api/**` CORS. 로컬은 Expo 개발 서버(웹 :8081), 배포 서버는 토스 인앱 미니앱 출처(#149)를 허용한다.
 * 배포 웹은 Vercel rewrites로 같은 출처라 CORS가 필요 없다.
 */
@Configuration
class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    WebCorsConfig(@Value("${review.cors.allowed-origins:}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length > 0) {
            registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
        }
    }
}
