package com.khack.review.engagement.adapter.in.scheduler;

import com.khack.review.engagement.application.DailyReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 학습 알림을 주기적으로 확인한다. 시각은 주입된 Clock 기준이라 시간 이동(`/dev/clock`) 뒤 다음 확인에서 반영된다.
 * {@code review.engagement.reminder.enabled=false}면 돌지 않는다(테스트).
 */
@Component
@ConditionalOnProperty(name = "review.engagement.reminder.enabled", havingValue = "true", matchIfMissing = true)
class DailyReminderScheduler {

    private final DailyReminderService reminders;

    DailyReminderScheduler(DailyReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${review.engagement.reminder.interval:30s}", initialDelayString = "${review.engagement.reminder.interval:30s}")
    void tick() {
        reminders.sendDue();
    }
}
