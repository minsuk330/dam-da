package com.khack.review.common.application.connector;

import com.khack.review.common.domain.ConnectorClient;
import com.khack.review.common.domain.ConnectorClientRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커넥터 OAuth 클라이언트 저장소(JPA). Claude가 동적 등록(DCR)한 클라이언트가 재시작 뒤에도 남는다.
 * <p>
 * 인가 코드 클라이언트는 토큰 수명을 늘려 저장한다: 접근 토큰 1시간, 갱신 토큰 30일.
 * 기본값(갱신 토큰 1시간)이면 Claude를 한 시간만 쓰지 않아도 다시 로그인해야 한다.
 */
@Service
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    static final TokenSettings CONNECTOR_TOKENS = TokenSettings.builder()
            .accessTokenTimeToLive(Duration.ofHours(1))
            .refreshTokenTimeToLive(Duration.ofDays(30))
            .build();

    private final ConnectorClientRepository clients;
    private final Clock clock;

    public JpaRegisteredClientRepository(ConnectorClientRepository clients, Clock clock) {
        this.clients = clients;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(RegisteredClient client) {
        RegisteredClient stored = client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)
                ? RegisteredClient.from(client).tokenSettings(CONNECTOR_TOKENS).build()
                : client;
        String data = Serialized.write(stored);
        clients.findById(stored.getId()).ifPresentOrElse(
                existing -> existing.update(stored.getClientId(), data, clock.instant()),
                () -> clients.save(new ConnectorClient(stored.getId(), stored.getClientId(), data, clock.instant())));
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findById(String id) {
        return clients.findById(id).map(c -> Serialized.read(c.getData(), RegisteredClient.class)).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findByClientId(String clientId) {
        return clients.findByClientId(clientId).map(c -> Serialized.read(c.getData(), RegisteredClient.class)).orElse(null);
    }
}
