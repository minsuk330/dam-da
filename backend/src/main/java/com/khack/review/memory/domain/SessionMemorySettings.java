package com.khack.review.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 학습 세션의 기억 강도 설정. 학습 세션은 ID로만 참조한다. */
@Entity
@Table(name = "session_memory_settings")
public class SessionMemorySettings {

    @Id
    private Long sessionId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryStrength strength;

    protected SessionMemorySettings() {
    }

    public SessionMemorySettings(Long sessionId, Long userId, MemoryStrength strength) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.strength = strength;
    }

    public void change(MemoryStrength strength) {
        this.strength = strength;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public MemoryStrength getStrength() {
        return strength;
    }
}
