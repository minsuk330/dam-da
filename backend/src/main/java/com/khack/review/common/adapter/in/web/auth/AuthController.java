package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.AuthTokenService;
import com.khack.review.common.application.AuthTokenService.AuthToken;
import com.khack.review.common.application.AuthTokenService.Me;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.UnauthenticatedException;
import com.khack.review.common.config.SocialLoginConfig.SocialRegistrations;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 로그인(스펙 §7.9). 앱은 {@code /api/auth/options}의 {@code loginUrl}로 이동해 소셜 로그인을 하고,
 * 돌아온 {@code /auth/callback?code=…}의 코드를 {@code /api/auth/token}으로 바꿔 Bearer 토큰으로 쓴다.
 */
@RestController
class AuthController {

    private final AuthTokenService tokens;
    private final CurrentUser currentUser;
    private final List<Provider> providers;
    private final boolean demoLogin;

    AuthController(AuthTokenService tokens, CurrentUser currentUser, SocialRegistrations social,
            @Value("${review.auth.server-url}") String serverUrl, @Value("${review.dev-tools.enabled:false}") boolean devTools) {
        this.tokens = tokens;
        this.currentUser = currentUser;
        String base = serverUrl.replaceAll("/+$", "");
        this.providers = social.registrations().stream()
                .map(r -> new Provider(r.getRegistrationId(), r.getClientName(), base + "/oauth2/authorization/" + r.getRegistrationId()))
                .toList();
        this.demoLogin = devTools;
    }

    /** {@code loginUrl}은 서버 주소 기준 절대 URL이다. 앱은 이 주소로 페이지를 이동한다. */
    record Provider(String id, String name, String loginUrl) {
    }

    /** {@code demoLogin}은 개발 도구가 켜진 환경에서만 true다. */
    record LoginOptions(List<Provider> providers, boolean demoLogin) {
    }

    record TokenRequest(String code) {
    }

    record ErrorResponse(String code, String message) {
    }

    @GetMapping("/api/auth/options")
    LoginOptions options() {
        return new LoginOptions(providers, demoLogin);
    }

    /** 소셜 로그인 후 받은 1회용 코드를 토큰으로 바꾼다. 코드는 한 번만 쓸 수 있고 2분 안에 써야 한다. */
    @PostMapping("/api/auth/token")
    AuthToken token(@RequestBody TokenRequest request) {
        return tokens.exchange(request.code()).orElseThrow(UnauthenticatedException::new);
    }

    /** 개발 환경 전용 "데모 계정으로 시작". 개발 도구가 꺼져 있으면 404. */
    @PostMapping("/api/auth/demo")
    ResponseEntity<AuthToken> demo() {
        if (!demoLogin) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tokens.issue(currentUser.demoUserId()));
    }

    @GetMapping("/api/me")
    Me me() {
        return tokens.me(currentUser.id());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ErrorResponse> unauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("unauthenticated", e.getMessage()));
    }
}
