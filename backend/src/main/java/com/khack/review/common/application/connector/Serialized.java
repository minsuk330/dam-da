package com.khack.review.common.application.connector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 인가 서버 객체(RegisteredClient, OAuth2Authorization)를 base64 문자열로 저장한다. 두 타입 모두 Serializable이다.
 * 서버가 직접 만든 값만 읽으므로 외부 입력을 역직렬화하지 않는다. Spring Security 버전을 올리면 기존 값은 못 읽을 수 있다(그때는 Claude 재연결).
 */
final class Serialized {

    private Serialized() {
    }

    static String write(Serializable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static <T> T read(String data, Class<T> type) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            return type.cast(in.readObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 토큰 값은 해시로만 저장하고 찾는다. */
    static String hash(String token) {
        if (token == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
