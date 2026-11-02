package com.khack.review.common.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.SocialSignInService.SocialProfile;
import com.khack.review.common.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SocialSignInServiceIT {

    @Autowired
    SocialSignInService signIn;

    @Test
    void firstLoginCreatesTheUserAndLaterLoginsKeepItWithTheNewProfile() {
        AppUser first = signIn.signIn(new SocialProfile("google", "sign-in-it", "민수", null));
        AppUser again = signIn.signIn(new SocialProfile("google", "sign-in-it", "김민수", "m@example.com"));
        AppUser blankNickname = signIn.signIn(new SocialProfile("google", "sign-in-it", " ", null));

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(blankNickname.displayName()).isEqualTo("김민수");
        assertThat(blankNickname.getEmail()).isEqualTo("m@example.com");
        assertThat(first.getName()).isEqualTo("google:sign-in-it");
    }

    @Test
    void newSocialAccountsRecordTheTermsVersionTheyAgreedToAtSignIn() {
        AppUser user = signIn.signIn(new SocialProfile("kakao", "consent-it", "규호", null));

        assertThat(user.getAgreedTermsVersion()).isEqualTo(AgreementService.CURRENT_VERSION);
        assertThat(user.getAgreedAt()).isNotNull();
        assertThat(AgreementService.required(user)).isFalse();
    }

    @Test
    void accountsWithoutTheCurrentVersionAgreeAgainInTheApp() {
        AppUser legacy = AppUser.social("google", "legacy", "옛 사용자", null, java.time.Instant.EPOCH);

        assertThat(AgreementService.required(legacy)).isTrue();
        legacy.agree("1999-01-01", java.time.Instant.EPOCH);
        assertThat(AgreementService.required(legacy)).as("예전 약관 버전").isTrue();
    }

    @Test
    void sameIdOnAnotherProviderIsAnotherUser() {
        AppUser google = signIn.signIn(new SocialProfile("google", "same-id", "A", null));
        AppUser kakao = signIn.signIn(new SocialProfile("kakao", "same-id", "A", null));

        assertThat(kakao.getId()).isNotEqualTo(google.getId());
    }
}
