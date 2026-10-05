package com.khack.review.common.application;

import com.khack.review.common.domain.AccountDeleted;
import com.khack.review.common.domain.AppUserRepository;
import com.khack.review.common.domain.ConnectorAuthorizationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴(#148). 유예 없이 바로, 한 트랜잭션으로 이 사용자의 데이터를 모두 지운다.
 * 각 컨텍스트는 {@link AccountDeleted}를 받아 자기 데이터를 지우고, 여기서는 Claude 커넥터 인가와 사용자를 지운다.
 * 이미 발급된 앱·커넥터 토큰은 사용자가 없으면 거부된다(SecurityConfig).
 */
@Service
public class AccountDeletionService {

    private final ApplicationEventPublisher events;
    private final ConnectorAuthorizationRepository connectorAuthorizations;
    private final AppUserRepository users;

    public AccountDeletionService(ApplicationEventPublisher events, ConnectorAuthorizationRepository connectorAuthorizations,
            AppUserRepository users) {
        this.events = events;
        this.connectorAuthorizations = connectorAuthorizations;
        this.users = users;
    }

    @Transactional
    public void delete(Long userId) {
        events.publishEvent(new AccountDeleted(userId));
        connectorAuthorizations.deleteByPrincipalName(userId.toString());
        users.deleteById(userId);
    }
}
