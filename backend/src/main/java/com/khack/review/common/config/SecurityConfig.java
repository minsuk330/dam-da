package com.khack.review.common.config;

import com.khack.review.common.application.AuthTokenService;
import com.khack.review.common.config.SocialLoginConfig.SocialRegistrations;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 앱 인증(스펙 §7.9).
 * <ul>
 *   <li>{@code /api/**}: 앱 토큰(Bearer JWT, {@code aud=review-app}) 필요. {@code /api/auth/**}만 열려 있다.</li>
 *   <li>{@code /oauth2/authorization/{google|kakao}} → 제공자 → {@code /login/oauth2/code/*}: 소셜 로그인(키가 있는 제공자만).</li>
 *   <li>{@code /dev/**}는 DevToolsFilter가, {@code /mcp}는 커넥터 OAuth(#96)가 맡는다. 여기서는 열어 둔다.</li>
 * </ul>
 * {@code review.auth.required=false}(테스트 프로파일)이면 {@code /api/**}도 열고, 토큰 없는 요청은 데모 사용자로 처리한다.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain appSecurity(HttpSecurity http, SocialRegistrations social,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> socialUsers, AuthenticationSuccessHandler loginSuccess,
            AuthenticationFailureHandler loginFailure, JwtDecoder appTokenDecoder,
            @Value("${review.auth.required:true}") boolean required) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/**").permitAll();
                    if (required) {
                        auth.requestMatchers("/api/**").authenticated();
                    }
                    auth.anyRequest().permitAll();
                })
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(appTokenDecoder)))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(new BearerTokenAuthenticationEntryPoint(),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
        if (!social.registrations().isEmpty()) {
            http.oauth2Login(login -> login
                    .clientRegistrationRepository(new InMemoryClientRegistrationRepository(social.registrations()))
                    .userInfoEndpoint(userInfo -> userInfo.userService(socialUsers))
                    .successHandler(loginSuccess)
                    .failureHandler(loginFailure));
        }
        return http.build();
    }

    @Bean
    JwtEncoder appTokenEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /** 이 서버가 발급한 앱 토큰만 받는다. 만료는 주입받은 Clock 기준이다(시간 이동 데모). */
    @Bean
    JwtDecoder appTokenDecoder(JWKSource<SecurityContext> jwkSource, Clock clock, @Value("${review.auth.server-url}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer),
                jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AuthTokenService.APP_AUDIENCE)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "앱 토큰이 아닙니다", null))));
        return decoder;
    }
}
