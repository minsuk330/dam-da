package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.SocialSignInService;
import com.khack.review.common.domain.AppUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 구글·카카오 사용자 정보를 받아 앱 사용자로 로그인시킨다. 인증 이름을 앱 사용자 ID로 바꿔서
 * {@code CurrentUser}와 커넥터 토큰(#96)의 {@code sub}가 모두 앱 사용자 ID가 되게 한다.
 */
@Component
class SocialOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    static final String APP_USER_ID = "appUserId";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
    private final SocialSignInService signIn;

    SocialOAuth2UserService(SocialSignInService signIn) {
        this.signIn = signIn;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User social = delegate.loadUser(request);
        AppUser user = signIn.signIn(SocialProfiles.from(request.getClientRegistration().getRegistrationId(), social.getAttributes()));
        Map<String, Object> attributes = new HashMap<>(social.getAttributes());
        attributes.put(APP_USER_ID, user.getId().toString());
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, APP_USER_ID);
    }
}
