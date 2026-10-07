package com.khack.review.common.config;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * `/api/**` CORS. 로컬은 Expo 개발 서버(웹 :8081), 배포 서버는 토스 인앱 미니앱 출처(#149)를 허용한다.
 * 앱 주소(`review.auth.app-url`)의 출처는 목록과 상관없이 항상 허용한다. 배포 웹은 Vercel rewrites로 부르지만
 * 브라우저가 붙인 Origin(Vercel 주소)이 그대로 넘어오므로, 목록에서 빠지면 로그인 POST가 403이 된다.
 */
@Configuration
class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    WebCorsConfig(@Value("${review.cors.allowed-origins:}") String[] allowedOrigins,
                  @Value("${review.auth.app-url:}") String appUrl) {
        this.allowedOrigins = Stream.concat(Arrays.stream(allowedOrigins), Stream.of(originOf(appUrl)))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length > 0) {
            registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE");
        }
    }

    /** {@code https://app.example/path/} → {@code https://app.example}. 출처로 읽을 수 없으면 빈 문자열. */
    private static String originOf(String url) {
        if (url.isBlank()) {
            return "";
        }
        URI uri = URI.create(url.strip());
        if (uri.getScheme() == null || uri.getHost() == null) {
            return "";
        }
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }
}
