package com.khack.review.common.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 토스 userKey를 DB에 평문으로 두지 않는다(앱인토스 출시 가이드: 로그인 정보 암호화 저장, #149).
 * 계정 조회에는 HMAC-SHA256 값을, 토스 API 호출(연결 끊기)에는 AES-256-GCM 암호문을 쓴다.
 * 두 키는 비밀 하나({@code TOSS_USER_KEY_SECRET}, base64 32바이트 이상)에서 용도별로 나눈다. 비밀을 바꾸면 기존 토스 계정을 찾지 못한다.
 */
@Component
public class TossUserKeyCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final byte[] lookupKey;
    private final byte[] encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public TossUserKeyCipher(@Value("${review.toss.user-key-secret:}") String secret) {
        byte[] master = secret.isBlank() ? new byte[0] : Base64.getDecoder().decode(secret.trim());
        if (master.length > 0 && master.length < 32) {
            throw new IllegalStateException("TOSS_USER_KEY_SECRET은 base64로 32바이트 이상이어야 합니다");
        }
        this.lookupKey = master.length == 0 ? null : hmac(master, "toss-user-key/lookup");
        this.encryptionKey = master.length == 0 ? null : hmac(master, "toss-user-key/encryption");
    }

    /** 비밀이 없으면 토스 로그인을 받지 않는다(평문 저장을 막는다). */
    public boolean configured() {
        return lookupKey != null;
    }

    /** 같은 userKey는 항상 같은 값이다. 계정 조회({@code provider_user_id})에 쓴다. */
    public String lookupId(String userKey) {
        requireConfigured();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(lookupKey, userKey));
    }

    public String encrypt(String userKey) {
        requireConfigured();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(userKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("토스 userKey 암호화 실패", e);
        }
    }

    public String decrypt(String stored) {
        requireConfigured();
        try {
            byte[] bytes = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("토스 userKey 복호화 실패", e);
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("TOSS_USER_KEY_SECRET이 설정되지 않았습니다");
        }
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
