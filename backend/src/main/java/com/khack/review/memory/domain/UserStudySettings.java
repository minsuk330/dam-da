package com.khack.review.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;

/** 사용자가 정한 매일 학습 시간 예산 (스펙 §7.7, §9.6). 없으면 설정 기본값(5분)을 쓴다. */
@Entity
@Table(name = "user_study_settings")
public class UserStudySettings {

    @Id
    private Long userId;

    @Column(nullable = false)
    private int dailyBudgetMinutes;

    protected UserStudySettings() {
    }

    public UserStudySettings(Long userId, int dailyBudgetMinutes) {
        this.userId = userId;
        this.dailyBudgetMinutes = dailyBudgetMinutes;
    }

    public void changeBudget(int minutes) {
        this.dailyBudgetMinutes = minutes;
    }

    public Long getUserId() {
        return userId;
    }

    public Duration dailyBudget() {
        return Duration.ofMinutes(dailyBudgetMinutes);
    }
}
