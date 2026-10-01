package com.khack.review.practice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 제시 안에서 도움을 보여 준 기록. 시도 구분과 직전 도움 노출·경과 시간의 근거다. */
@Entity
@Table(name = "aid_exposure")
public class AidExposure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long presentationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AidType type;

    @Column(nullable = false)
    private Instant exposedAt;

    protected AidExposure() {
    }

    public AidExposure(Long presentationId, AidType type, Instant exposedAt) {
        this.presentationId = presentationId;
        this.type = type;
        this.exposedAt = exposedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPresentationId() {
        return presentationId;
    }

    public AidType getType() {
        return type;
    }

    public Instant getExposedAt() {
        return exposedAt;
    }
}
