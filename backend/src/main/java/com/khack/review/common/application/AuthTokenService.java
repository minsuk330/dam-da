package com.khack.review.common.application;

import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 로그인 토큰(스펙 §7.9). 소셜 로그인이 끝나면 서버가 1회용 코드를 앱으로 넘기고, 앱은 그 코드를 토큰으로 바꾼다.
 * 토큰을 URL에 싣지 않으려고 코드를 한 번 거친다. 코드는 메모리에만 두며 짧게 유효하다(서버 1대 전제).
 */
@Service
public class AuthTokenService {

    /** 앱 토큰의 {@code aud}. 커넥터 토큰(#96)과 섞이지 않게 구분한다. */
    public static final String APP_AUDIENCE = "review-app";

    private static final Duration TOKEN_TTL = Duration.ofDays(30);
    private static final Duration CODE_TTL = Duration.ofMinutes(2);

    private final JwtEncoder encoder;
    private final AppUserRepository users;
    private final Clock clock;
    private final String issuer;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();

    public AuthTokenService(JwtEncoder encoder, AppUserRepository users, Clock clock, @Value("${review.auth.server-url}") String issuer) {
        this.encoder = encoder;
        this.users = users;
        this.clock = clock;
        this.issuer = issuer;
    }

    /** {@code agreementRequired}: 대화를 저장하기 전에 앱 안에서 약관 동의가 필요하다(토스 익명 계정, #149). */
    public record Me(Long id, String name, boolean agreementRequired) {
    }

    public record AuthToken(String accessToken, Instant expiresAt, Me user) {
    }

    private record PendingCode(Long userId, Instant expiresAt) {
    }

    /** 소셜 로그인 직후. 앱으로 넘길 1회용 코드를 만든다. */
    public String issueCode(Long userId) {
        Instant now = clock.instant();
        codes.values().removeIf(code -> code.expiresAt().isBefore(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new PendingCode(userId, now.plus(CODE_TTL)));
        return code;
    }

    /** 1회용 코드를 토큰으로 바꾼다. 한 번 쓰면 사라진다. 없거나 만료됐으면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<AuthToken> exchange(String code) {
        PendingCode pending = code == null ? null : codes.remove(code);
        if (pending == null || pending.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(issue(pending.userId()));
    }

    @Transactional(readOnly = true)
    public AuthToken issue(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(UnauthenticatedException::new);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(TOKEN_TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .audience(List.of(APP_AUDIENCE))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new AuthToken(token, expiresAt, me(user));
    }

    @Transactional(readOnly = true)
    public Me me(Long userId) {
        return me(users.findById(userId).orElseThrow(UnauthenticatedException::new));
    }

    private static Me me(AppUser user) {
        return new Me(user.getId(), user.displayName(), AgreementService.required(user));
    }
}
