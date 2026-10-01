package com.khack.review.engagement.application;

import com.khack.review.engagement.application.port.out.PushNotifier;
import com.khack.review.engagement.domain.DailyReminderSettings;
import com.khack.review.engagement.domain.DailyReminderSettingsRepository;
import com.khack.review.engagement.domain.Notification;
import com.khack.review.engagement.domain.NotificationRepository;
import com.khack.review.memory.application.StudySettingsService;
import com.khack.review.practice.application.DailyPracticeService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매일 학습 설정(하루 시간 예산, 알림 시각)과 알림 (스펙 §7.7, §9.6, 도메인 스토리 S2-1). 알림은 주입된 {@link Clock} 기준으로
 * 알림 시각이 지난 뒤 하루 한 번 "오늘의 학습 · 약 N분"을 앱 안 알림으로 만든다. 이미 끝냈거나 큐가 빈 날은 보내지 않는다.
 */
@Service
public class DailyReminderService {

    private static final Logger log = LoggerFactory.getLogger(DailyReminderService.class);

    private final DailyReminderSettingsRepository settings;
    private final StudySettingsService study;
    private final DailyPracticeService dailyPractice;
    private final StreakService streaks;
    private final NotificationRepository notifications;
    private final PushNotifier push;
    private final Clock clock;

    public DailyReminderService(DailyReminderSettingsRepository settings, StudySettingsService study,
            DailyPracticeService dailyPractice, StreakService streaks, NotificationRepository notifications, PushNotifier push,
            Clock clock) {
        this.settings = settings;
        this.study = study;
        this.dailyPractice = dailyPractice;
        this.streaks = streaks;
        this.notifications = notifications;
        this.push = push;
        this.clock = clock;
    }

    /** {@code notifyAt}이 null이면 매일 학습 알림을 받지 않는다. */
    public record Settings(int budgetMinutes, @Nullable LocalTime notifyAt) {
    }

    @Transactional(readOnly = true)
    public Settings settings(Long userId) {
        return new Settings((int) study.budget(userId).toMinutes(),
                settings.findById(userId).map(DailyReminderSettings::getNotifyAt).orElse(null));
    }

    /** 하루 예산이 범위를 벗어나면 {@link IllegalArgumentException}. */
    @Transactional
    public Settings change(Long userId, int budgetMinutes, @Nullable LocalTime notifyAt) {
        study.changeBudget(userId, budgetMinutes);
        settings.findById(userId).ifPresentOrElse(existing -> existing.changeNotifyAt(notifyAt),
                () -> settings.save(new DailyReminderSettings(userId, notifyAt)));
        return settings(userId);
    }

    /** 알림 시각이 지난 사용자에게 오늘의 알림을 만든다. 만든 알림을 돌려준다. */
    @Transactional
    public List<Notification> sendDue() {
        ZonedDateTime now = clock.instant().atZone(clock.getZone());
        LocalDate today = now.toLocalDate();
        List<Notification> sent = new ArrayList<>();
        for (DailyReminderSettings reminder : settings.findByNotifyAtIsNotNull()) {
            if (reminder.isDue(today, now.toLocalTime())) {
                remind(reminder.getUserId(), today).ifPresent(sent::add);
                reminder.reminded(today);
            }
        }
        return sent;
    }

    private Optional<Notification> remind(Long userId, LocalDate today) {
        DailyPracticeService.DailyView view = dailyPractice.today(userId);
        if (view.completed()) {
            return Optional.empty();
        }
        if (!view.started() && view.total() == 0) {
            streaks.markEmpty(userId, today);
            return Optional.empty();
        }
        int minutes = (int) Math.max(1, Math.ceilDiv(view.estimatedSeconds(), 60));
        Notification saved = notifications.save(Notification.dailyLearning(userId, minutes, view.total(), view.started(),
                view.practiceId(), clock.instant()));
        try {
            push.push(saved.getUserId(), saved.getTitle(), saved.getBody());
        } catch (RuntimeException e) {
            log.warn("push failed for notification {}", saved.getId(), e);
        }
        return Optional.of(saved);
    }
}
