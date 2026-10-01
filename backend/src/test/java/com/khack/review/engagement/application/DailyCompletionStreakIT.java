package com.khack.review.engagement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.engagement.domain.DailyRecordRepository;
import com.khack.review.engagement.domain.DailyStatus;
import com.khack.review.engagement.domain.StreakUpdated;
import com.khack.review.practice.application.PracticeService;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** 매일 학습 풀이를 끝내면(`PracticeService.next`가 완료) 그날이 완료로 기록되고 `StreakUpdated`가 나온다. */
@SpringBootTest
@Import(DailyCompletionStreakIT.Events.class)
class DailyCompletionStreakIT {

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

    @Autowired
    PracticeService practice;

    @Autowired
    PracticeSessionRepository practices;

    @Autowired
    DailyRecordRepository records;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    TimeTravelClock clock;

    @Autowired
    Received received;

    @Test
    void finishingTodaysDailyPracticeCountsTheDay() {
        Long user = currentUser.id();
        PracticeSession daily = practices.save(PracticeSession.daily(user, List.of(), clock.instant()));
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();

        assertThat(practice.next(daily.getId()).done()).isTrue();
        assertThat(practice.next(daily.getId()).done()).isTrue();

        assertThat(records.findByUserIdAndDate(user, today)).hasValueSatisfying(
                record -> assertThat(record.getStatus()).isEqualTo(DailyStatus.COMPLETED));
        assertThat(received.streaks).as("처음 끝낼 때 한 번").singleElement().satisfies(event -> {
            assertThat(event.userId()).isEqualTo(user);
            assertThat(event.date()).isEqualTo(today);
            assertThat(event.current()).isPositive();
        });
        assertThat(practices.findById(daily.getId()).orElseThrow().getKind()).isEqualTo(PracticeKind.DAILY);
    }
}
