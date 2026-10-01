package com.khack.review.engagement.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyReminderSettingsRepository extends JpaRepository<DailyReminderSettings, Long> {

    List<DailyReminderSettings> findByNotifyAtIsNotNull();
}
