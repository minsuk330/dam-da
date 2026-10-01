package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.SocialSignInService.SocialProfile;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** 제공자별 사용자 정보 응답을 공통 프로필로 바꾼다. */
final class SocialProfiles {

    private SocialProfiles() {
    }

    static SocialProfile from(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            // https://developers.google.com/identity/openid-connect/openid-connect#obtainuserinfo
            case "google" -> new SocialProfile(provider, required(attributes, "sub"), text(attributes.get("name")),
                    text(attributes.get("email")));
            // https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#req-user-info
            case "kakao" -> {
                Map<?, ?> account = map(attributes.get("kakao_account"));
                Map<?, ?> profile = map(account.get("profile"));
                String nickname = text(profile.get("nickname"));
                yield new SocialProfile(provider, required(attributes, "id"),
                        nickname != null ? nickname : text(map(attributes.get("properties")).get("nickname")),
                        text(account.get("email")));
            }
            default -> throw new IllegalArgumentException("지원하지 않는 로그인 제공자: " + provider);
        };
    }

    private static String required(Map<String, Object> attributes, String key) {
        String value = text(attributes.get(key));
        if (value == null) {
            throw new IllegalArgumentException("사용자 정보에 " + key + "가 없습니다");
        }
        return value;
    }

    private static Map<?, ?> map(@Nullable Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static @Nullable String text(@Nullable Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
