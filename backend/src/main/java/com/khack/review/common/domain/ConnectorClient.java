package com.khack.review.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 커넥터 OAuth 클라이언트(스펙 §7.9). Claude가 연결할 때 동적 등록(DCR)한 클라이언트를 보관한다.
 * 클라이언트 설정 전체는 직렬화해 {@code data}에 둔다(인가 서버만 읽는다).
 */
@Entity
@Table(name = "connector_client")
public class ConnectorClient {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String clientId;

    @Column(nullable = false, length = 100_000)
    private String data;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ConnectorClient() {
    }

    public ConnectorClient(String id, String clientId, String data, Instant updatedAt) {
        this.id = id;
        this.clientId = clientId;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public void update(String clientId, String data, Instant updatedAt) {
        this.clientId = clientId;
        this.data = data;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getData() {
        return data;
    }
}
