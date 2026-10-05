package com.khack.review.common.application;

import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 사용자를 앱 사용자로 잇는다(스펙 §7.9). 처음 로그인하면 계정을 만들고(공개 가입), 이후에는 프로필만 갱신한다.
 * 같은 사람이라도 제공자가 다르면 다른 계정이다.
 */
@Service
public class SocialSignInService {

    private final AppUserRepository users;
    private final Clock clock;

    public SocialSignInService(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public record SocialProfile(String provider, String providerUserId, @Nullable String nickname, @Nullable String email) {
    }

    @Transactional
    public AppUser signIn(SocialProfile profile) {
        return signIn(profile, null);
    }

    /** {@code encryptedProviderUserId}가 있으면 함께 저장한다(토스, {@link TossUserKeyCipher}). */
    @Transactional
    public AppUser signIn(SocialProfile profile, @Nullable String encryptedProviderUserId) {
        AppUser user = users.findByProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(found -> {
                    found.updateProfile(profile.nickname(), profile.email());
                    return found;
                })
                .orElseGet(() -> users.save(AppUser.social(profile.provider(), profile.providerUserId(), profile.nickname(),
                        profile.email(), clock.instant())));
        if (encryptedProviderUserId != null) {
            user.encryptedProviderUserId(encryptedProviderUserId);
        }
        return user;
    }
}
