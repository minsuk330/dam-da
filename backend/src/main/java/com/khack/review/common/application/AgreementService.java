package com.khack.review.common.application;

import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관 동의(#149, #192). 어느 약관 버전에 언제 동의했는지 사용자에 기록한다.
 * <ul>
 *   <li>웹 소셜 로그인·토스 로그인: 로그인 화면의 안내로 동의받고, 계정을 만들 때 지금 버전으로 기록한다({@link SocialSignInService}).</li>
 *   <li>토스 익명 계정: 로그인 없이 만들어지므로 대화를 처음 저장할 때 앱 안에서 동의받는다(노출 정책 UX 원칙: 동의는 관련 기능을 고른 뒤에).</li>
 *   <li>약관 버전이 바뀌었거나 기록이 없는 계정(#192 이전 가입)은 다음 저장 때 앱 안에서 다시 동의받는다.</li>
 * </ul>
 * 시작할 때 만드는 데모·개발 도구 사용자(제공자 없음)는 묻지 않는다.
 */
@Service
public class AgreementService {

    /** 지금 약관 버전. 약관 시행일이다. 바꾸면 토스 익명 계정은 다음 저장 때 다시 동의한다. */
    public static final String CURRENT_VERSION = "2026-10-05";

    private final AppUserRepository users;
    private final Clock clock;

    public AgreementService(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    /** 이 사용자가 앱 안에서 약관 동의를 해야 하는지. */
    public static boolean required(AppUser user) {
        return user.getProvider() != null && !CURRENT_VERSION.equals(user.getAgreedTermsVersion());
    }

    /** 로그인 화면의 안내로 동의받는 계정인지(토스 익명 계정은 앱 안에서 따로 받는다). */
    static boolean agreesAtSignIn(String provider) {
        return !TossSignInService.ANONYMOUS_PROVIDER.equals(provider);
    }

    @Transactional
    public void agree(Long userId, String version) {
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalArgumentException("약관 버전이 맞지 않습니다. 앱을 새로 열어 주세요.");
        }
        users.findById(userId).orElseThrow(UnauthenticatedException::new).agree(version, clock.instant());
    }

    /** 대화를 저장하기 전에 부른다. 동의가 필요하면 {@link AgreementRequiredException}(403). */
    @Transactional(readOnly = true)
    public void requireAgreed(Long userId) {
        users.findById(userId).filter(AgreementService::required).ifPresent(user -> {
            throw new AgreementRequiredException();
        });
    }
}
