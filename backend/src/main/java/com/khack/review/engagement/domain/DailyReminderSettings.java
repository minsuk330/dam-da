package com.khack.review.engagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/** 매일 학습 알림 시각 (스펙 §7.7, §9.6). 통학 시간대처럼 사용자가 정한 시각에 하루 한 번 앱 안 알림을 만든다. */
@Entity
@Table(name = "daily_reminder_settings")
public class DailyReminderSettings {

    @Id
    private Long userId;

    /** 알림 시각(서버 시계의 시간대). null이면 알림을 보내지 않는다. */
    private LocalTime notifyAt;

    /** 마지막으로 알림을 처리한 날. 하루에 한 번만 보낸다. */
    private LocalDate lastRemindedOn;

    protected DailyReminderSettings() {
    }

    public DailyReminderSettings(Long userId, @Nullable LocalTime notifyAt) {
        this.userId = userId;
        this.notifyAt = notifyAt;
    }

    public void changeNotifyAt(@Nullable LocalTime notifyAt) {
        this.notifyAt = notifyAt;
    }

    /** 오늘 알림을 보낼 차례인가: 알림 시각이 지났고 오늘 아직 처리하지 않았다. */
    public boolean isDue(LocalDate today, LocalTime now) {
        return notifyAt != null && !now.isBefore(notifyAt) && !today.equals(lastRemindedOn);
    }

    public void reminded(LocalDate on) {
        lastRemindedOn = on;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalTime getNotifyAt() {
        return notifyAt;
    }

    public LocalDate getLastRemindedOn() {
        return lastRemindedOn;
    }
}
