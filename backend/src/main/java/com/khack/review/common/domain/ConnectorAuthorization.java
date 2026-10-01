package com.khack.review.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 커넥터 인가 한 건(스펙 §7.9): 사용자가 Claude 연결을 승인한 기록과 발급한 코드·토큰.
 * 토큰 값은 해시로만 찾고, 인가 전체는 직렬화해 {@code data}에 둔다. 재시작해도 Claude 연결(갱신 토큰)이 유지된다.
 */
@Entity
@Table(name = "connector_authorization", indexes = {
        @Index(columnList = "stateHash"), @Index(columnList = "codeHash"),
        @Index(columnList = "accessTokenHash"), @Index(columnList = "refreshTokenHash")})
public class ConnectorAuthorization {

    @Id
    private String id;

    @Column(nullable = false)
    private String registeredClientId;

    /** 앱 사용자 ID(문자열). */
    @Column(nullable = false)
    private String principalName;

    private String stateHash;

    private String codeHash;

    private String accessTokenHash;

    private String refreshTokenHash;

    @Column(nullable = false, length = 100_000)
    private String data;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ConnectorAuthorization() {
    }

    public ConnectorAuthorization(String id) {
        this.id = id;
    }

    public void update(String registeredClientId, String principalName, String stateHash, String codeHash, String accessTokenHash,
            String refreshTokenHash, String data, Instant updatedAt) {
        this.registeredClientId = registeredClientId;
        this.principalName = principalName;
        this.stateHash = stateHash;
        this.codeHash = codeHash;
        this.accessTokenHash = accessTokenHash;
        this.refreshTokenHash = refreshTokenHash;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getData() {
        return data;
    }
}
