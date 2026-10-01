package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 소셜 로그인이 끝난 뒤 어디로 보낼지 정한다.
 * <ul>
 *   <li>로그인이 필요해 중간에 멈춘 요청이 있으면(커넥터 연결 승인, #96) 그 요청으로 돌아간다.</li>
 *   <li>앱에서 시작한 로그인이면 {@code {app-url}/auth/callback?code=…}로 보낸다. 앱이 코드를 토큰으로 바꾼다.</li>
 * </ul>
 * 돌아갈 앱 주소는 설정값으로만 정한다(요청 값으로 정하지 않아 열린 리다이렉트를 막는다).
 */
@Component
public class SocialLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginHandlers.class);

    private final AuthTokenService tokens;
    private final String callbackUrl;
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final SavedRequestAwareAuthenticationSuccessHandler savedRequest = new SavedRequestAwareAuthenticationSuccessHandler();

    SocialLoginHandlers(AuthTokenService tokens, @Value("${review.auth.app-url}") String appUrl) {
        this.tokens = tokens;
        this.callbackUrl = appUrl.replaceAll("/+$", "") + "/auth/callback";
        savedRequest.setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, jakarta.servlet.ServletException {
        if (requestCache.getRequest(request, response) != null) {
            savedRequest.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        String code = tokens.issueCode(Long.valueOf(authentication.getName()));
        response.sendRedirect(UriComponentsBuilder.fromUriString(callbackUrl).queryParam("code", code).build().toUriString());
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.warn("소셜 로그인 실패: {}", exception.getMessage());
        response.sendRedirect(UriComponentsBuilder.fromUriString(callbackUrl).queryParam("error", "login_failed").build().toUriString());
    }
}
