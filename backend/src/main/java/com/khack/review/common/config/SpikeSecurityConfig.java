package com.khack.review.common.config;

import static org.springaicommunity.mcp.security.authorizationserver.config.McpAuthorizationServerConfigurer.mcpAuthorizationServer;
import static org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer.mcpServerOAuth2;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** #94 스파이크: 한 앱에서 MCP 인가 서버 + /mcp 리소스 서버. 버리는 코드. */
@Configuration
class SpikeSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .with(mcpAuthorizationServer(), mcp -> mcp.authorizationServer(authz -> http.securityMatcher(
                        new OrRequestMatcher(authz.getEndpointsMatcher(),
                                PathPatternRequestMatcher.withDefaults().matcher("/.well-known/openid-configuration")))))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain mcpChain(HttpSecurity http, JWKSource<SecurityContext> jwkSource,
            @Value("${review.auth.issuer}") String issuer) throws Exception {
        return http.securityMatcher("/mcp", "/mcp/**", "/.well-known/oauth-protected-resource/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .with(mcpServerOAuth2(), mcp -> mcp.authorizationServer(issuer)
                        .jwtDecoder(org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSource(jwkSource).build()))
                .build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain appChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login").permitAll()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .formLogin(Customizer.withDefaults())
                .build();
    }

    @Bean
    UserDetailsService spikeUsers(@Value("${SPIKE_PASSWORD:spike}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername("spike-user").password("{noop}" + password).roles("USER").build());
    }

    @Bean
    RegisteredClientRepository registeredClients() {
        // DCR로 추가되는 클라이언트를 담는다. 비어 있으면 안 돼서 쓰지 않는 기본 클라이언트를 하나 둔다.
        return new InMemoryRegisteredClientRepository(RegisteredClient.withId("default").clientId("default")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build());
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${review.auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(UUID.randomUUID().toString()).build();
        return new ImmutableJWKSet<>(new JWKSet(key));
    }
}
