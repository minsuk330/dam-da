package com.khack.review.engagement.application;

import com.khack.review.common.domain.AccountDeleted;
import com.khack.review.engagement.domain.DailyRecordRepository;
import com.khack.review.engagement.domain.DailyReminderSettingsRepository;
import com.khack.review.engagement.domain.NotificationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴한 사용자의 알림·학습 기록·알림 설정을 지운다(#148). */
@Component
class EngagementAccountDeletion {

    private final NotificationRepository notifications;
    private final DailyRecordRepository records;
    private final DailyReminderSettingsRepository reminders;

    EngagementAccountDeletion(NotificationRepository notifications, DailyRecordRepository records,
            DailyReminderSettingsRepository reminders) {
        this.notifications = notifications;
        this.records = records;
        this.reminders = reminders;
    }

    @EventListener
    void on(AccountDeleted event) {
        Long userId = event.userId();
        notifications.deleteByUserId(userId);
        records.deleteByUserId(userId);
        reminders.deleteByUserId(userId);
    }
}
