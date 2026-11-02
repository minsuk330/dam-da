package com.khack.review.common.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.RequestRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 분당 한도를 거는 경로(#184). LLM·Jev를 부르는 요청과 로그인 전 인증 요청만 센다. */
@Configuration
class RateLimitConfig implements WebMvcConfigurer {

    private final RequestRateLimiter limiter;
    private final CurrentUser currentUser;
    private final int aiRequestsPerMinute;
    private final int authRequestsPerMinute;

    RateLimitConfig(RequestRateLimiter limiter, CurrentUser currentUser,
            @Value("${review.limits.ai-requests-per-minute}") int aiRequestsPerMinute,
            @Value("${review.limits.auth-requests-per-minute}") int authRequestsPerMinute) {
        this.limiter = limiter;
        this.currentUser = currentUser;
        this.aiRequestsPerMinute = aiRequestsPerMinute;
        this.authRequestsPerMinute = authRequestsPerMinute;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(limiter, currentUser, aiRequestsPerMinute, authRequestsPerMinute))
                .addPathPatterns(
                        "/api/auth/**",
                        "/api/conversations/**",
                        "/api/sessions/*/first-study/practice",
                        "/api/practice/presentations/*/attempts",
                        "/api/practice/presentations/*/aids",
                        "/api/practice/presentations/*/feedback");
    }
}
