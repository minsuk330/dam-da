package com.khack.review.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 사용자가 지금 쓰는 FSRS 매개변수 버전. 없으면 기본값(버전 1)을 쓴다. */
@Entity
@Table(name = "user_fsrs_settings")
public class UserFsrsSettings {

    @Id
    private Long userId;

    @Column(nullable = false)
    private int activeParametersVersion;

    protected UserFsrsSettings() {
    }

    public UserFsrsSettings(Long userId, int activeParametersVersion) {
        this.userId = userId;
        this.activeParametersVersion = activeParametersVersion;
    }

    public void activate(int version) {
        this.activeParametersVersion = version;
    }

    public Long getUserId() {
        return userId;
    }

    public int getActiveParametersVersion() {
        return activeParametersVersion;
    }
}
