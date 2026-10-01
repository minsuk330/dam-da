package com.khack.review.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 학습 세션의 학습 분야 라벨 (스펙 §7.10). 세션당 주 소분류 1개이며 값은 분류표의 소분류 {@code code}다.
 * 비동기 자동 판정과 확인 단계 수정이 세션 엔티티 갱신과 겹치지 않도록 별도 테이블에 둔다. 세션은 ID로만 참조한다.
 */
@Entity
@Table(name = "session_field")
public class SessionField {

    @Id
    private Long sessionId;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldSource source;

    /** 자동 판정의 근거(선택·신뢰도·모델). 사용자가 고르면 null이다. */
    @Column(length = 1000)
    private String reason;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SessionField() {
    }

    public static SessionField classified(Long sessionId, String code, String reason, Instant at) {
        SessionField field = new SessionField();
        field.sessionId = sessionId;
        field.classify(code, reason, at);
        return field;
    }

    public static SessionField chosen(Long sessionId, String code, Instant at) {
        SessionField field = new SessionField();
        field.sessionId = sessionId;
        field.choose(code, at);
        return field;
    }

    /** 자동 판정 결과를 적는다. 사용자가 이미 골랐으면 덮어쓰지 않는다(규칙 19). */
    public void classify(String code, String reason, Instant at) {
        if (source == FieldSource.USER) {
            throw new IllegalStateException("학습 세션 %d의 분야는 사용자가 골랐습니다.".formatted(sessionId));
        }
        this.code = code;
        this.source = FieldSource.AUTO;
        this.reason = reason;
        this.updatedAt = at;
    }

    public void choose(String code, Instant at) {
        this.code = code;
        this.source = FieldSource.USER;
        this.reason = null;
        this.updatedAt = at;
    }

    public boolean isChosenByUser() {
        return source == FieldSource.USER;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public String getCode() {
        return code;
    }

    public FieldSource getSource() {
        return source;
    }

    public String getReason() {
        return reason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
