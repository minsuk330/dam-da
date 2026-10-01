package com.khack.review.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 개발용 CORS. Expo 개발 서버(웹 :8081)는 Spring과 출처가 달라 `/api/**`만 허용한다.
 * 배포 웹은 Vercel rewrites로 같은 출처라 CORS가 필요 없으므로 배포 서버에서는 비워 둔다.
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
