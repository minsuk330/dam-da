package com.khack.review.engagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.common.json.Json;
import com.khack.review.engagement.domain.DailyStatus;
import com.khack.review.engagement.domain.Notification;
import com.khack.review.engagement.domain.NotificationType;
import com.khack.review.engagement.domain.Streak;
import com.khack.review.engagement.domain.StreakUpdated;
import com.khack.review.memory.application.StudySettingsService;
import com.khack.review.practice.application.DailyPracticeService;
import com.khack.review.practice.application.DailyPracticeService.DailyView;
import com.khack.review.practice.domain.DailyPracticeCompleted;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

/**
 * 매일 학습 설정·알림·연속 학습 일수 (스펙 §7.7, 도메인 스토리 S2-1·9). 오늘의 학습 상태는 가짜로 정해 알림·빈 날 규칙만 본다.
 * 다른 테스트와 DB를 공유하므로 사용자 ID를 따로 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DailyHabitIT.Events.class)
class DailyHabitIT {

    static final AtomicLong USERS = new AtomicLong(990_000);

    @TestConfiguration
    static class Events {

        @Bean
        Received received() {
            return new Received();
        }
    }

    static class Received {

        final List<StreakUpdated> streaks = new CopyOnWriteArrayList<>();

        @EventListener
        void on(StreakUpdated event) {
            streaks.add(event);
        }
    }

    @MockitoBean
    DailyPracticeService dailyPractice;

    @Autowired
    DailyReminderService reminders;

    @Autowired
    StreakService streaks;

    @Autowired
    StudySettingsService study;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TimeTravelClock clock;

    @Autowired
    Received received;

    @Value("${local.server.port}")
    int port;

    @BeforeEach
    void reset() {
        received.streaks.clear();
        today(0, 0, false, false);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** 가짜 오늘의 학습: 문제 수, 예상 초, 시작했는가, 끝냈는가. */
    void today(int questions, long seconds, boolean started, boolean completed) {
        when(dailyPractice.today(anyLong())).thenReturn(new DailyView(date(), started ? 7L : null, started, completed, questions,
                questions, 0, seconds, List.of(), 0, List.of()));
    }

    LocalDate date() {
        return clock.instant().atZone(clock.getZone()).toLocalDate();
    }

    void complete(long userId) {
        events.publishEvent(new DailyPracticeCompleted(userId, 1L, date()));
    }

    @Test
    void streakGrowsKeepsOnEmptyDaysAndBreaksAfterAMissedDay() {
        long user = USERS.incrementAndGet();

        complete(user);
        assertThat(streaks.streak(user)).isEqualTo(new Streak(1, 1, DailyStatus.COMPLETED));
        assertThat(received.streaks).containsExactly(new StreakUpdated(user, date(), 1, 1));

        clock.travel(Duration.ofDays(1));
        today(3, 120, false, false);
        assertThat(streaks.streak(user).current()).as("오늘은 아직 끝나지 않아 유지").isEqualTo(1);
        complete(user);
        assertThat(streaks.streak(user).current()).isEqualTo(2);

        clock.travel(Duration.ofDays(1));
        today(0, 0, false, false);
        assertThat(streaks.streak(user)).as("복습할 항목이 없는 날은 유지").isEqualTo(new Streak(2, 2, DailyStatus.EMPTY));

        clock.travel(Duration.ofDays(1));
        today(2, 80, false, false);
        complete(user);
        assertThat(streaks.streak(user).current()).isEqualTo(3);

        clock.travel(Duration.ofDays(2));
        today(2, 80, true, false);
        assertThat(streaks.streak(user)).as("못 끝낸 날이 지나면 끊긴다").isEqualTo(new Streak(0, 3, null));
        complete(user);
        assertThat(streaks.streak(user)).isEqualTo(new Streak(1, 3, DailyStatus.COMPLETED));
    }

    @Test
    void reminderComesOnceAfterTheChosenTime() {
        long user = USERS.incrementAndGet();
        ZonedDateTime now = clock.instant().atZone(clock.getZone());
        LocalTime notifyAt = now.toLocalTime().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        if (notifyAt.isBefore(now.toLocalTime())) {
            clock.travel(Duration.ofHours(2));
            notifyAt = clock.instant().atZone(clock.getZone()).toLocalTime().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        }
        reminders.change(user, 10, notifyAt);
        today(4, 190, false, false);

        assertThat(mine(reminders.sendDue(), user)).as("알림 시각 전").isEmpty();

        clock.travel(Duration.ofMinutes(61));
        List<Notification> sent = mine(reminders.sendDue(), user);
        assertThat(sent).singleElement().satisfies(notification -> {
            assertThat(notification.getType()).isEqualTo(NotificationType.DAILY_LEARNING);
            assertThat(notification.getTitle()).isEqualTo("오늘의 학습 · 약 4분");
            assertThat(notification.getBody()).contains("4개");
        });
        assertThat(mine(reminders.sendDue(), user)).as("하루 한 번").isEmpty();

        clock.travel(Duration.ofDays(1));
        today(4, 190, true, true);
        assertThat(mine(reminders.sendDue(), user)).as("이미 끝낸 날").isEmpty();

        clock.travel(Duration.ofDays(1));
        today(0, 0, false, false);
        assertThat(mine(reminders.sendDue(), user)).as("큐가 빈 날").isEmpty();
        assertThat(streaks.streak(user).today()).isEqualTo(DailyStatus.EMPTY);

        reminders.change(user, 10, null);
        clock.travel(Duration.ofDays(1));
        today(4, 190, false, false);
        assertThat(mine(reminders.sendDue(), user)).as("알림 끔").isEmpty();
    }

    @Test
    void settingsApiChangesBudgetAndReminderTime() throws Exception {
        assertThat(json("GET", "/api/settings/daily", null, 200).get("budgetMinutes").asInt()).isPositive();

        JsonNode changed = json("PUT", "/api/settings/daily", "{\"budgetMinutes\":8,\"notifyAt\":\"07:30\"}", 200);
        assertThat(changed.get("budgetMinutes").asInt()).isEqualTo(8);
        assertThat(changed.get("notifyAt").asString()).startsWith("07:30");
        assertThat(json("GET", "/api/settings/daily", null, 200).get("notifyAt").asString()).startsWith("07:30");
        json("PUT", "/api/settings/daily", "{\"budgetMinutes\":0,\"notifyAt\":null}", 400);
        json("PUT", "/api/settings/daily", "{\"budgetMinutes\":5,\"notifyAt\":null}", 200);

        JsonNode streak = json("GET", "/api/streak", null, 200);
        assertThat(streak.has("current")).isTrue();
        assertThat(streak.has("best")).isTrue();
    }

    @Test
    void userBudgetFeedsTheDailyQueue() {
        long user = USERS.incrementAndGet();
        study.changeBudget(user, 12);

        assertThat(study.budget(user)).isEqualTo(Duration.ofMinutes(12));
        assertThat(study.budget(USERS.incrementAndGet())).as("기본값").isEqualTo(Duration.ofMinutes(5));
    }

    static List<Notification> mine(List<Notification> sent, long user) {
        return sent.stream().filter(notification -> notification.getUserId() == user).toList();
    }

    JsonNode json(String method, String path, String body, int status) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        return Json.MAPPER.readTree(response.body());
    }
}
