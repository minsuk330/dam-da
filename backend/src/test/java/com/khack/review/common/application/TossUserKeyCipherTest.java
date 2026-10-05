package com.khack.review.common.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TossUserKeyCipherTest {

    static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void lookupIsStableAndHidesTheUserKey() {
        TossUserKeyCipher cipher = new TossUserKeyCipher(SECRET);

        assertThat(cipher.lookupId("443731104")).isEqualTo(cipher.lookupId("443731104"))
                .isNotEqualTo(cipher.lookupId("443731105"))
                .doesNotContain("443731104");
        assertThat(new TossUserKeyCipher(Base64.getEncoder().encodeToString("another-secret-another-secret-32".getBytes()))
                .lookupId("443731104")).isNotEqualTo(cipher.lookupId("443731104"));
    }

    @Test
    void encryptsWithAFreshIvAndDecryptsBack() {
        TossUserKeyCipher cipher = new TossUserKeyCipher(SECRET);

        String first = cipher.encrypt("443731104");
        String second = cipher.encrypt("443731104");

        assertThat(first).isNotEqualTo(second).doesNotContain("443731104");
        assertThat(cipher.decrypt(first)).isEqualTo("443731104");
        assertThat(cipher.decrypt(second)).isEqualTo("443731104");
    }

    @Test
    void tamperedCiphertextFails() {
        TossUserKeyCipher cipher = new TossUserKeyCipher(SECRET);
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("443731104"));
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptySecretIsUnconfiguredAndShortSecretIsRejected() {
        TossUserKeyCipher empty = new TossUserKeyCipher("");

        assertThat(empty.configured()).isFalse();
        assertThatThrownBy(() -> empty.lookupId("1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TossUserKeyCipher(Base64.getEncoder().encodeToString("short".getBytes())))
                .isInstanceOf(IllegalStateException.class);
    }
}
