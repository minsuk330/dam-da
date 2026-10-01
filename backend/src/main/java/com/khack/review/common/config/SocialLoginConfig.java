package com.khack.review.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * 구글·카카오 로그인 등록. 키가 있는 제공자만 켠다(테스트·키 없는 로컬은 소셜 로그인 없이 뜬다).
 * redirect URI는 {@code {baseUrl}/login/oauth2/code/{google|kakao}}이며 각 콘솔에 같은 값을 등록한다(docs/verification/2026-10-02-mcp-oauth-spike.md).
 */
@Configuration
public class SocialLoginConfig {

    private static final String REDIRECT_URI = "{baseUrl}/login/oauth2/code/{registrationId}";

    /** 켜진 제공자. 비어 있을 수 있다. */
    public record SocialRegistrations(List<ClientRegistration> registrations) {
    }

    @Bean
    SocialRegistrations socialRegistrations(
            @Value("${review.auth.google.client-id:}") String googleId,
            @Value("${review.auth.google.client-secret:}") String googleSecret,
            @Value("${review.auth.kakao.client-id:}") String kakaoId,
            @Value("${review.auth.kakao.client-secret:}") String kakaoSecret) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (!googleId.isBlank()) {
            // openid를 빼서 OIDC가 아닌 OAuth2 사용자 정보로 받는다(두 제공자를 같은 방식으로 처리).
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google").clientName("구글")
                    .clientId(googleId).clientSecret(googleSecret).scope("profile", "email").redirectUri(REDIRECT_URI).build());
        }
        if (!kakaoId.isBlank()) {
            // Client Secret은 카카오 콘솔에서 켰을 때만 넣는다. 없으면 REST API 키만으로 토큰을 받는다.
            ClientRegistration.Builder kakao = ClientRegistration.withRegistrationId("kakao")
                    .clientName("카카오")
                    .clientId(kakaoId)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(REDIRECT_URI)
                    .scope("profile_nickname")
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id");
            if (kakaoSecret.isBlank()) {
                kakao.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
            } else {
                kakao.clientSecret(kakaoSecret).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
            }
            registrations.add(kakao.build());
        }
        return new SocialRegistrations(List.copyOf(registrations));
    }
}
