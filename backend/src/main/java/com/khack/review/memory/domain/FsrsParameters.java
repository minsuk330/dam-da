package com.khack.review.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * FSRS 매개변수 묶음 (스펙 §6.4.9). 버전으로 구분하며, 복습 기록은 계산에 쓴 버전을 남긴다.
 * 기본값은 버전 1이고, 옵티마이저가 검증을 통과한 값을 새 버전으로 추가한다.
 */
@Entity
@Table(name = "fsrs_parameters")
public class FsrsParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int version;

    /** 쉼표로 구분한 매개변수 {@value FsrsSchedulers#PARAMETER_COUNT}개. */
    @Column(nullable = false, length = 1_000)
    private String weights;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParameterSource source;

    /** 옵티마이저 검증 결과 요약(JSON 등). 기본값은 null. */
    @Column(length = 10_000)
    private String validation;

    @Column(nullable = false)
    private Instant createdAt;

    /** 개인 매개변수의 주인. 기본값은 모든 사용자가 쓰므로 null이다. 다른 사용자는 이 버전을 적용할 수 없다. */
    private Long ownerUserId;

    protected FsrsParameters() {
    }

    public FsrsParameters(int version, double[] weights, ParameterSource source, String validation, Instant createdAt) {
        this(version, weights, source, validation, createdAt, null);
    }

    public FsrsParameters(int version, double[] weights, ParameterSource source, String validation, Instant createdAt,
            Long ownerUserId) {
        if (weights.length != FsrsSchedulers.PARAMETER_COUNT) {
            throw new IllegalArgumentException("FSRS 매개변수는 %d개여야 합니다: %d".formatted(FsrsSchedulers.PARAMETER_COUNT, weights.length));
        }
        this.version = version;
        this.weights = Arrays.stream(weights).mapToObj(Double::toString).collect(Collectors.joining(","));
        this.source = source;
        this.validation = validation;
        this.createdAt = createdAt;
        this.ownerUserId = ownerUserId;
    }

    /** 이 사용자가 쓸 수 있는가. 기본값은 누구나, 개인 매개변수는 주인만. */
    public boolean usableBy(Long userId) {
        return ownerUserId == null || ownerUserId.equals(userId);
    }

    public double[] weights() {
        return Arrays.stream(weights.split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    public Long getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public ParameterSource getSource() {
        return source;
    }

    public String getValidation() {
        return validation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }
}
