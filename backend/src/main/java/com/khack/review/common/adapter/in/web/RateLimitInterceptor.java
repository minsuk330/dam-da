package com.khack.review.common.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.RateLimitedException;
import com.khack.review.common.application.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 비용이 드는 요청의 분당 한도(#184). 로그인 요청은 사용자별, 로그인 전 요청({@code /api/auth/**})은 IP별로 센다.
 * IP는 프록시 헤더를 반영한 원격 주소다(server.forward-headers-strategy).
 */
class RateLimitInterceptor implements HandlerInterceptor {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final RequestRateLimiter limiter;
    private final CurrentUser currentUser;
    private final int aiRequestsPerMinute;
    private final int authRequestsPerMinute;

    RateLimitInterceptor(RequestRateLimiter limiter, CurrentUser currentUser, int aiRequestsPerMinute, int authRequestsPerMinute) {
        this.limiter = limiter;
        this.currentUser = currentUser;
        this.aiRequestsPerMinute = aiRequestsPerMinute;
        this.authRequestsPerMinute = authRequestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        if (request.getRequestURI().startsWith("/api/auth/")) {
            if (!limiter.tryAcquire("auth:" + request.getRemoteAddr(), authRequestsPerMinute, MINUTE)) {
                throw new RateLimitedException("요청이 너무 많아요. 1분 뒤 다시 시도해 주세요.");
            }
            return true;
        }
        if (!limiter.tryAcquire("ai:" + currentUser.id(), aiRequestsPerMinute, MINUTE)) {
            throw new RateLimitedException("요청이 너무 많아요. 잠시 뒤 다시 시도해 주세요.");
        }
        return true;
    }
}
