package com.khack.review.common.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.common.application.SocialSignInService.SocialProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SocialProfilesTest {

    @Test
    void readsGoogleUserInfo() {
        SocialProfile profile = SocialProfiles.from("google", Map.of("sub", "1234", "name", "김민수", "email", "a@example.com"));

        assertThat(profile).isEqualTo(new SocialProfile("google", "1234", "김민수", "a@example.com"));
    }

    @Test
    void readsKakaoNicknameFromTheAccountProfileOrProperties() {
        SocialProfile fromAccount = SocialProfiles.from("kakao", Map.of("id", 98765L,
                "kakao_account", Map.of("profile", Map.of("nickname", "민수"))));
        SocialProfile fromProperties = SocialProfiles.from("kakao", Map.of("id", 98765L, "properties", Map.of("nickname", "민수2")));

        assertThat(fromAccount).isEqualTo(new SocialProfile("kakao", "98765", "민수", null));
        assertThat(fromProperties.nickname()).isEqualTo("민수2");
    }

    @Test
    void needsTheProviderUserId() {
        assertThatThrownBy(() -> SocialProfiles.from("kakao", Map.of("properties", Map.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SocialProfiles.from("naver", Map.of("id", 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
