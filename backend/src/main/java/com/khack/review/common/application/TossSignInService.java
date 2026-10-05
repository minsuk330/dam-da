package com.khack.review.common.application;

import com.khack.review.common.application.AuthTokenService.AuthToken;
import com.khack.review.common.application.SocialSignInService.SocialProfile;
import com.khack.review.common.application.port.out.TossLoginException;
import com.khack.review.common.application.port.out.TossLoginPort;
import com.khack.review.common.application.port.out.TossLoginPort.TossUser;
import com.khack.review.common.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 토스 인앱(앱인토스) 로그인(#149). 미니앱 안에서는 토스 로그인만 쓸 수 있다(서비스 오픈 정책 2-3).
 * 토스 사용자는 {@code provider=toss}, {@code providerUserId=HMAC(userKey)}인 소셜 계정으로 잇는다. 처음이면 계정을 만든다.
 * userKey 평문은 DB에 두지 않는다({@link TossUserKeyCipher}).
 * 개인정보(이름 등)는 암호화돼 오고 복호화 키를 아직 쓰지 않으므로 화면 이름은 기본값이다.
 */
@Service
public class TossSignInService {

    public static final String PROVIDER = "toss";
    /** 로그인 없이 익명 식별키로 만든 토스 계정(#149). {@code providerUserId=HMAC(anonKey)}. */
    public static final String ANONYMOUS_PROVIDER = "toss-anon";
    static final String DEFAULT_NICKNAME = "토스 사용자";

    private static final Logger log = LoggerFactory.getLogger(TossSignInService.class);

    private final TossLoginPort toss;
    private final SocialSignInService social;
    private final AuthTokenService tokens;
    private final TossUserKeyCipher cipher;

    public TossSignInService(TossLoginPort toss, SocialSignInService social, AuthTokenService tokens, TossUserKeyCipher cipher) {
        this.toss = toss;
        this.social = social;
        this.tokens = tokens;
        this.cipher = cipher;
    }

    /**
     * 토스 인앱 기본 진입(#149). 미니앱이 {@code createAnonymousKeyAuthCode()}로 받은 코드를 앱 토큰으로 바꾼다. 로그인·동의 화면이 없다.
     * 처음이면 계정을 만들고, 대화를 저장할 때 약관 동의를 받는다({@link AgreementService}). 코드가 거절되면 401.
     */
    public AuthToken signInAnonymously(String code) {
        if (code == null || code.isBlank()) {
            throw new UnauthenticatedException();
        }
        if (!cipher.configured()) {
            log.warn("[toss-anon] TOSS_USER_KEY_SECRET이 없어 익명 계정을 만들지 않습니다");
            throw new UnauthenticatedException();
        }
        String anonKey;
        try {
            anonKey = toss.anonymousKey(code);
        } catch (TossLoginException e) {
            log.warn("[toss-anon] {}", e.getMessage());
            throw new UnauthenticatedException();
        }
        AppUser appUser = social.signIn(new SocialProfile(ANONYMOUS_PROVIDER, cipher.lookupId(anonKey), DEFAULT_NICKNAME, null));
        return tokens.issue(appUser.getId());
    }

    /** 인가 코드를 앱 토큰으로 바꾼다. 토스가 코드를 거절하면 401({@link UnauthenticatedException})이다. */
    public AuthToken signIn(String authorizationCode, String referrer) {
        if (authorizationCode == null || authorizationCode.isBlank() || referrer == null || referrer.isBlank()) {
            throw new UnauthenticatedException();
        }
        if (!cipher.configured()) {
            log.warn("[toss-login] TOSS_USER_KEY_SECRET이 없어 토스 로그인을 받지 않습니다");
            throw new UnauthenticatedException();
        }
        TossUser user;
        try {
            user = toss.login(authorizationCode, referrer);
        } catch (TossLoginException e) {
            log.warn("[toss-login] {}", e.getMessage());
            throw new UnauthenticatedException();
        }
        AppUser appUser = social.signIn(new SocialProfile(PROVIDER, cipher.lookupId(user.userKey()), DEFAULT_NICKNAME, null),
                cipher.encrypt(user.userKey()));
        return tokens.issue(appUser.getId());
    }
}
