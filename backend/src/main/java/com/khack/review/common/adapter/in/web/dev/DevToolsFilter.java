package com.khack.review.common.adapter.in.web.dev;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * `/dev/**` 개발 도구(시간 이동, 대화 원문이 보이는 세션 뷰어)를 보호한다.
 * <ul>
 *   <li>`review.dev-tools.enabled`가 꺼져 있으면 모두 404.</li>
 *   <li>이 기기에서 직접 온 요청은 허용한다. 프록시·터널을 거친 요청은 로컬로 보지 않는다.</li>
 *   <li>원격 요청은 `X-Dev-Token` 헤더가 `review.dev-tools.token`과 같을 때만 허용한다. 토큰이 비어 있으면 원격은 모두 막는다.</li>
 * </ul>
 * 막을 때는 존재 자체를 숨기도록 404를 돌려준다.
 */
@Component
class DevToolsFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL_HOSTNAMES = Set.of("localhost", "127.0.0.1", "[::1]");

    static final String TOKEN_HEADER = "X-Dev-Token";

    private final boolean enabled;
    private final byte[] token;

    DevToolsFilter(@Value("${review.dev-tools.enabled:false}") boolean enabled,
            @Value("${review.dev-tools.token:}") String token) {
        this.enabled = enabled;
        this.token = token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/dev") || path.startsWith("/dev/") || path.startsWith("/dev."));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !(isLocal(request) || hasValidToken(request))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasValidToken(HttpServletRequest request) {
        String presented = request.getHeader(TOKEN_HEADER);
        return token != null && presented != null
                && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
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
