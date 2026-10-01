package com.khack.review.engagement.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.engagement.application.DailyReminderService;
import com.khack.review.engagement.application.StreakService;
import com.khack.review.engagement.domain.Streak;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 매일 학습 설정(하루 시간 예산, 알림 시각)과 연속 학습 일수 (스펙 §7.7). */
@RestController
class DailyHabitController {

    private final DailyReminderService reminders;
    private final StreakService streaks;
    private final CurrentUser currentUser;

    DailyHabitController(DailyReminderService reminders, StreakService streaks, CurrentUser currentUser) {
        this.reminders = reminders;
        this.streaks = streaks;
        this.currentUser = currentUser;
    }

    /** {@code notifyAt}은 "07:30" 형식이고 null이면 알림을 끈다. */
    record SettingsRequest(int budgetMinutes, @Nullable LocalTime notifyAt) {
    }

    record ErrorResponse(String code, String message) {
    }

    @GetMapping("/api/settings/daily")
    DailyReminderService.Settings settings() {
        return reminders.settings(currentUser.id());
    }

    @PutMapping("/api/settings/daily")
    DailyReminderService.Settings change(@RequestBody SettingsRequest request) {
        return reminders.change(currentUser.id(), request.budgetMinutes(), request.notifyAt());
    }

    @GetMapping("/api/streak")
    Streak streak() {
        return streaks.streak(currentUser.id());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage()));
    }
}
