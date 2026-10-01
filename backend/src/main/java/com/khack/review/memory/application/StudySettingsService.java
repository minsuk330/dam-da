package com.khack.review.memory.application;

import com.khack.review.memory.domain.UserStudySettings;
import com.khack.review.memory.domain.UserStudySettingsRepository;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매일 학습 시간 예산. 매일 학습 큐가 이 값으로 분량을 자른다(스펙 §6.4.4). */
@Service
public class StudySettingsService {

    /** 하루 예산으로 고를 수 있는 범위(분). */
    public static final int MIN_BUDGET_MINUTES = 1;
    public static final int MAX_BUDGET_MINUTES = 60;

    private final UserStudySettingsRepository settings;
    private final DailyQueueProperties defaults;

    public StudySettingsService(UserStudySettingsRepository settings, DailyQueueProperties defaults) {
        this.settings = settings;
        this.defaults = defaults;
    }

    @Transactional(readOnly = true)
    public Duration budget(Long userId) {
        return settings.findById(userId).map(UserStudySettings::dailyBudget).orElse(defaults.budget());
    }

    /** 범위를 벗어나면 {@link IllegalArgumentException}. */
    @Transactional
    public Duration changeBudget(Long userId, int minutes) {
        if (minutes < MIN_BUDGET_MINUTES || minutes > MAX_BUDGET_MINUTES) {
            throw new IllegalArgumentException("하루 학습 시간은 %d~%d분 사이로 정하세요.".formatted(MIN_BUDGET_MINUTES, MAX_BUDGET_MINUTES));
        }
        settings.findById(userId).ifPresentOrElse(existing -> existing.changeBudget(minutes),
                () -> settings.save(new UserStudySettings(userId, minutes)));
        return Duration.ofMinutes(minutes);
    }
}
