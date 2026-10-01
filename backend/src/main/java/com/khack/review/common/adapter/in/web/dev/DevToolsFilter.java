package com.khack.review.common.adapter.in.web.dev;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * `/dev/**` 개발 도구를 보호한다. `review.dev-tools.enabled`가 꺼져 있거나, 이 기기에서 직접 온 요청이 아니면
 * 404를 돌려준다. 검증 중 서버를 터널로 노출하므로 프록시를 거친 요청도 막는다.
 */
@Component
class DevToolsFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL_HOSTNAMES = Set.of("localhost", "127.0.0.1", "[::1]");

    private final boolean enabled;

    DevToolsFilter(@Value("${review.dev-tools.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/dev") || path.startsWith("/dev/") || path.startsWith("/dev."));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !isLocal(request)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isLocal(HttpServletRequest request) {
        if (request.getHeader("X-Forwarded-For") != null || request.getHeader("Forwarded") != null) {
            return false;
        }
        if (!LOCAL_HOSTNAMES.contains(request.getServerName())) {
            return false;
        }
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
