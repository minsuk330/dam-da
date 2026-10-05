package com.khack.review.common.application;

import com.khack.review.common.application.port.out.TossLoginException;
import com.khack.review.common.application.port.out.TossLoginPort;
import com.khack.review.common.domain.AccountDeleted;
import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import com.khack.review.common.domain.ConnectorAuthorizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회원 탈퇴(#148). 유예 없이 바로, 한 트랜잭션으로 이 사용자의 데이터를 모두 지운다.
 * 각 컨텍스트는 {@link AccountDeleted}를 받아 자기 데이터를 지우고, 여기서는 Claude 커넥터 인가와 사용자를 지운다.
 * 이미 발급된 앱·커넥터 토큰은 사용자가 없으면 거부된다(SecurityConfig).
 * 토스 사용자는 커밋 뒤 토스 로그인 연결도 끊는다(#149). 실패해도 탈퇴는 되돌리지 않는다.
 */
@Service
public class AccountDeletionService {

    private final ApplicationEventPublisher events;
    private final ConnectorAuthorizationRepository connectorAuthorizations;
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final AppUserRepository users;
    private final TossLoginPort toss;
    private final TossUserKeyCipher tossKeys;

    public AccountDeletionService(ApplicationEventPublisher events, ConnectorAuthorizationRepository connectorAuthorizations,
            AppUserRepository users, TossLoginPort toss, TossUserKeyCipher tossKeys) {
        this.events = events;
        this.connectorAuthorizations = connectorAuthorizations;
        this.users = users;
        this.toss = toss;
        this.tossKeys = tossKeys;
    }

    /** 앱에서 탈퇴한다. 토스 사용자는 토스 로그인 연결도 끊는다. */
    @Transactional
    public void delete(Long userId) {
        users.findById(userId)
                .filter(user -> TossSignInService.PROVIDER.equals(user.getProvider()) && user.getEncryptedProviderUserId() != null)
                .map(AppUser::getEncryptedProviderUserId)
                .ifPresent(this::disconnectTossAfterCommit);
        deleteData(userId);
    }

    /** 사용자가 토스 앱에서 연결을 끊었다(연결 끊기 콜백). 이미 끊겼으므로 데이터만 지운다. */
    @Transactional
    public void deleteUnlinked(Long userId) {
        deleteData(userId);
    }

    private void deleteData(Long userId) {
        events.publishEvent(new AccountDeleted(userId));
        connectorAuthorizations.deleteByPrincipalName(userId.toString());
        users.deleteById(userId);
    }

    private void disconnectTossAfterCommit(String encryptedUserKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    toss.disconnect(tossKeys.decrypt(encryptedUserKey));
                } catch (TossLoginException | IllegalStateException e) {
                    log.warn("[toss-login] 탈퇴 후 토스 연결 끊기 실패: {}", e.getMessage());
                }
            }
        });
    }
}
