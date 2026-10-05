package com.khack.review.common.application;

import com.khack.review.common.domain.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 토스 로그인 연결 끊기 콜백(#149). 사용자가 토스 앱에서 연결을 끊거나({@code UNLINK}), 약관 동의를 철회하거나({@code WITHDRAWAL_TERMS}),
 * 토스를 탈퇴하면({@code WITHDRAWAL_TOSS}) 토스가 부른다. 어느 경우든 이 사용자의 데이터를 모두 지운다(출시 가이드: 연결을 끊으면 데이터가 남지 않아야 함).
 * 토스는 콘솔에 등록한 값을 Basic Auth 헤더로 보낸다. 디코딩한 값이 {@code TOSS_UNLINK_BASIC_AUTH}와 같아야 한다.
 */
@Service
public class TossUnlinkService {

    private static final Logger log = LoggerFactory.getLogger(TossUnlinkService.class);

    private final AppUserRepository users;
    private final AccountDeletionService accounts;
    private final TossUserKeyCipher cipher;
    private final byte[] expectedCredentials;

    public TossUnlinkService(AppUserRepository users, AccountDeletionService accounts, TossUserKeyCipher cipher,
            @Value("${review.toss.unlink-basic-auth:}") String expectedCredentials) {
        this.users = users;
        this.accounts = accounts;
        this.cipher = cipher;
        this.expectedCredentials = expectedCredentials.getBytes(StandardCharsets.UTF_8);
    }

    /** {@code Authorization} 헤더가 콘솔에 등록한 Basic Auth 값인지. 설정이 비어 있으면 모두 거절한다. */
    public boolean authorized(String authorization) {
        if (expectedCredentials.length == 0 || authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(authorization.substring(6).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(decoded, expectedCredentials);
    }

    /** 이 userKey의 토스 계정과 데이터를 지운다. 계정이 없으면(이미 탈퇴 등) 아무것도 하지 않는다. */
    public void unlink(String userKey, String referrer) {
        if (userKey == null || userKey.isBlank() || !cipher.configured()) {
            log.warn("[toss-unlink] 처리하지 않음: userKey 없음 또는 TOSS_USER_KEY_SECRET 없음 (referrer={})", referrer);
            return;
        }
        users.findByProviderAndProviderUserId(TossSignInService.PROVIDER, cipher.lookupId(userKey))
                .ifPresentOrElse(user -> {
                    accounts.deleteUnlinked(user.getId());
                    log.info("[toss-unlink] 사용자 {} 데이터 삭제 (referrer={})", user.getId(), referrer);
                }, () -> log.info("[toss-unlink] 해당 계정 없음 (referrer={})", referrer));
    }
}
