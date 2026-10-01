package com.khack.review.common.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 앱 로그인 토큰(과 #96의 커넥터 토큰)에 서명하는 RSA 키.
 * {@code review.auth.signing-key}(AUTH_SIGNING_KEY)는 PKCS#8 DER 개인 키를 base64 한 줄로 넣는다.
 * 만들기: {@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 | tr -d '\n'}
 * <p>
 * 비어 있으면 시작할 때마다 새 키를 만든다(로컬·테스트용). 그러면 재시작할 때 발급한 토큰이 모두 무효가 되므로 배포 서버는 반드시 넣는다.
 */
@Configuration
class AuthKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthKeyConfig.class);

    @Bean
    JWKSource<SecurityContext> authJwkSource(@Value("${review.auth.signing-key:}") String signingKey)
            throws GeneralSecurityException, JOSEException {
        RSAKey key = signingKey.isBlank() ? generated() : parse(signingKey.strip());
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    private static RSAKey parse(String base64) throws GeneralSecurityException, JOSEException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        var publicKey = (RSAPublicKey) factory.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }

    private static RSAKey generated() throws GeneralSecurityException, JOSEException {
        log.warn("review.auth.signing-key가 비어 있어 임시 서명 키를 만듭니다. 재시작하면 로그인이 풀립니다(배포 서버에서는 AUTH_SIGNING_KEY를 넣으세요).");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).build();
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }
}
