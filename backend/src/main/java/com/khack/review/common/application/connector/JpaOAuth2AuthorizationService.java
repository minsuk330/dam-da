package com.khack.review.common.application.connector;

import com.khack.review.common.domain.ConnectorAuthorization;
import com.khack.review.common.domain.ConnectorAuthorizationRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커넥터 인가 저장소(JPA). 기본 구현(JdbcTemplate)은 AGENTS.md의 "영속성은 JPA만" 규칙에 맞지 않아 직접 둔다.
 * state·인가 코드·접근 토큰·갱신 토큰으로 찾을 수 있다.
 */
@Service
public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private static final OAuth2TokenType STATE = new OAuth2TokenType(OAuth2ParameterNames.STATE);
    private static final OAuth2TokenType CODE = new OAuth2TokenType(OAuth2ParameterNames.CODE);

    private final ConnectorAuthorizationRepository authorizations;
    private final Clock clock;

    public JpaOAuth2AuthorizationService(ConnectorAuthorizationRepository authorizations, Clock clock) {
        this.authorizations = authorizations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        ConnectorAuthorization entity = authorizations.findById(authorization.getId())
                .orElseGet(() -> new ConnectorAuthorization(authorization.getId()));
        entity.update(authorization.getRegisteredClientId(), authorization.getPrincipalName(),
                Serialized.hash(authorization.getAttribute(OAuth2ParameterNames.STATE)),
                tokenHash(authorization.getToken(OAuth2AuthorizationCode.class)),
                tokenHash(authorization.getToken(OAuth2AccessToken.class)),
                tokenHash(authorization.getToken(OAuth2RefreshToken.class)),
                Serialized.write(authorization), clock.instant());
        authorizations.save(entity);
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        authorizations.deleteById(authorization.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findById(String id) {
        return read(authorizations.findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        String hash = Serialized.hash(token);
        if (tokenType == null) {
            return read(authorizations.findByAnyTokenHash(hash));
        }
        if (STATE.equals(tokenType)) {
            return read(authorizations.findByStateHash(hash));
        }
        if (CODE.equals(tokenType)) {
            return read(authorizations.findByCodeHash(hash));
        }
        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return read(authorizations.findByAccessTokenHash(hash));
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return read(authorizations.findByRefreshTokenHash(hash));
        }
        return null;
    }

    private static String tokenHash(OAuth2Authorization.Token<? extends OAuth2Token> token) {
        return token == null ? null : Serialized.hash(token.getToken().getTokenValue());
    }

    private static OAuth2Authorization read(Optional<ConnectorAuthorization> entity) {
        return entity.map(e -> Serialized.read(e.getData(), OAuth2Authorization.class)).orElse(null);
    }
}
