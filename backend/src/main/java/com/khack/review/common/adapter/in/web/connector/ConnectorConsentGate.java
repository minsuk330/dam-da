package com.khack.review.common.adapter.in.web.connector;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 커넥터 연결 승인(스펙 §7.9). 로그인한 사용자가 인가 요청({@code GET /oauth2/authorize})에 처음 오면 승인 화면으로 보낸다.
 * 승인하면 같은 요청으로 돌아오고, 이때 한 번만 통과시킨다. Claude는 연결할 때마다 클라이언트를 새로 등록하므로 매번 묻는다.
 * <p>
 * 인가 서버 체인에만 넣는다(빈으로 등록하지 않는다).
 */
public class ConnectorConsentGate extends OncePerRequestFilter {

    static final String APPROVED = ConnectorConsentGate.class.getName() + ".approved";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("GET".equals(request.getMethod()) && "/oauth2/authorize".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication user = SecurityContextHolder.getContext().getAuthentication();
        String query = request.getQueryString();
        if (user == null || !user.isAuthenticated() || user instanceof AnonymousAuthenticationToken || query == null
                || consume(request.getSession(), query)) {
            chain.doFilter(request, response);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/connect/consent?" + query);
    }

    /** 승인 화면에서 허용한 인가 요청을 기록한다. */
    static void approve(HttpSession session, String query) {
        approvals(session).add(query);
    }

    private static boolean consume(HttpSession session, String query) {
        return approvals(session).remove(query);
    }

    @SuppressWarnings("unchecked")
    private static synchronized Set<String> approvals(HttpSession session) {
        Object approvals = session.getAttribute(APPROVED);
        if (approvals == null) {
            approvals = new HashSet<String>();
            session.setAttribute(APPROVED, approvals);
        }
        return (Set<String>) approvals;
    }
}
