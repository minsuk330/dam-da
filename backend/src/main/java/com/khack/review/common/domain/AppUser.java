package com.khack.review.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 사용자. 사용자 소유 엔티티는 이 ID를 참조한다(스펙 §7.9).
 * 소셜 로그인 사용자는 {@code provider + providerUserId}로 찾고, {@code name}은 겹치지 않는 내부 이름({@code google:123})이다.
 * 데모·합성 사용자는 소셜 계정 없이 이름으로만 존재한다.
 */
@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"}))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    /** 소셜 로그인 제공자({@code google}, {@code kakao}). 데모·합성 사용자는 null. */
    private String provider;

    private String providerUserId;

    /**
     * 토스 사용자의 userKey 암호문(AES-GCM, #149). 토스는 {@code providerUserId}에 userKey의 HMAC을 두고, 토스 API(연결 끊기)를 부를 때만 이 값을 푼다.
     * 다른 제공자는 null.
     */
    private String encryptedProviderUserId;

    /** 마지막으로 동의한 약관 버전과 시각(#149). 토스 익명 계정만 앱에서 따로 동의한다. 웹 소셜 로그인은 로그인을 동의로 본다. */
    private String agreedTermsVersion;

    private Instant agreedAt;

    /** 화면에 보이는 이름. 소셜 닉네임이며 없으면 {@code name}을 쓴다. */
    private String nickname;

    private String email;

    protected AppUser() {
    }

    public AppUser(String name, Instant createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }

    public static AppUser social(String provider, String providerUserId, String nickname, String email, Instant createdAt) {
        AppUser user = new AppUser(provider + ":" + providerUserId, createdAt);
        user.provider = provider;
        user.providerUserId = providerUserId;
        user.updateProfile(nickname, email);
        return user;
    }

    /** 로그인할 때마다 제공자가 준 최신 프로필로 맞춘다. 비어 있는 값은 덮어쓰지 않는다. */
    public void updateProfile(String nickname, String email) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
    }

    public void encryptedProviderUserId(String encrypted) {
        this.encryptedProviderUserId = encrypted;
    }

    public String getEncryptedProviderUserId() {
        return encryptedProviderUserId;
    }

    public void agree(String termsVersion, Instant at) {
        this.agreedTermsVersion = termsVersion;
        this.agreedAt = at;
    }

    public String getAgreedTermsVersion() {
        return agreedTermsVersion;
    }

    public String displayName() {
        return nickname != null ? nickname : name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }
}
