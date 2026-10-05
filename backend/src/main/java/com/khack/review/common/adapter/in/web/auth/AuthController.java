package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.AccountDeletionService;
import com.khack.review.common.application.AgreementService;
import com.khack.review.common.application.AuthTokenService;
import com.khack.review.common.application.AuthTokenService.AuthToken;
import com.khack.review.common.application.AuthTokenService.Me;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.TossSignInService;
import com.khack.review.common.application.UnauthenticatedException;
import com.khack.review.common.config.SocialLoginConfig.SocialRegistrations;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 로그인(스펙 §7.9). 앱은 {@code /api/auth/options}의 {@code loginUrl}로 이동해 소셜 로그인을 하고,
 * 돌아온 {@code /auth/callback?code=…}의 코드를 {@code /api/auth/token}으로 바꿔 Bearer 토큰으로 쓴다.
 */
@RestController
class AuthController {

    private final AuthTokenService tokens;
    private final CurrentUser currentUser;
    private final AccountDeletionService accounts;
    private final TossSignInService toss;
    private final AgreementService agreements;
    private final List<Provider> providers;

    AuthController(AuthTokenService tokens, CurrentUser currentUser, AccountDeletionService accounts, TossSignInService toss,
            AgreementService agreements, SocialRegistrations social, @Value("${review.auth.server-url}") String serverUrl) {
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.toss = toss;
        this.agreements = agreements;
        String base = serverUrl.replaceAll("/+$", "");
        this.providers = social.registrations().stream()
                .map(r -> new Provider(r.getRegistrationId(), r.getClientName(), base + "/oauth2/authorization/" + r.getRegistrationId()))
                .toList();
    }

    /** {@code loginUrl}은 서버 주소 기준 절대 URL이다. 앱은 이 주소로 페이지를 이동한다. */
    record Provider(String id, String name, String loginUrl) {
    }

    record LoginOptions(List<Provider> providers) {
    }

    record TokenRequest(String code) {
    }

    /** 미니앱 {@code appLogin()}의 결과 그대로. {@code referrer}는 {@code DEFAULT}(토스 앱) 또는 {@code SANDBOX}. */
    record TossLoginRequest(String authorizationCode, String referrer) {
    }

    /** 미니앱 {@code User.createAnonymousKeyAuthCode()}의 {@code code}(5분, 1회용). */
    record TossAnonymousRequest(String code) {
    }

    /** 동의한 약관 버전. 앱이 보여준 약관의 시행일이다. */
    record AgreementRequest(String version) {
    }

    record ErrorResponse(String code, String message) {
    }

    @GetMapping("/api/auth/options")
    LoginOptions options() {
        return new LoginOptions(providers);
    }

    /** 소셜 로그인 후 받은 1회용 코드를 토큰으로 바꾼다. 코드는 한 번만 쓸 수 있고 2분 안에 써야 한다. */
    @PostMapping("/api/auth/token")
    AuthToken token(@RequestBody TokenRequest request) {
        return tokens.exchange(request.code()).orElseThrow(UnauthenticatedException::new);
    }

    /** 토스 인앱(#149). 토스 로그인 인가 코드를 앱 토큰으로 바꾼다. 처음 로그인하면 계정이 만들어진다. */
    @PostMapping("/api/auth/toss")
    AuthToken toss(@RequestBody TossLoginRequest request) {
        return toss.signIn(request.authorizationCode(), request.referrer());
    }

    /** 토스 인앱 기본 진입(#149). 로그인 없이 익명 식별키로 계정을 찾거나 만든다. */
    @PostMapping("/api/auth/toss/anonymous")
    AuthToken tossAnonymous(@RequestBody TossAnonymousRequest request) {
        return toss.signInAnonymously(request.code());
    }

    /** 약관 동의(#149). 토스 익명 계정이 대화를 처음 저장할 때 앱이 부른다. 버전이 지금 약관과 다르면 400. */
    @PostMapping("/api/me/agreements")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void agree(@RequestBody AgreementRequest request) {
        agreements.agree(currentUser.id(), request.version());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage()));
    }

    @GetMapping("/api/me")
    Me me() {
        return tokens.me(currentUser.id());
    }

    /** 회원 탈퇴. 이 사용자의 데이터를 바로 모두 지우며 되돌릴 수 없다. 이후 이 사용자의 토큰은 401이다. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMe() {
        accounts.delete(currentUser.id());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ErrorResponse> unauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("unauthenticated", e.getMessage()));
    }
}
