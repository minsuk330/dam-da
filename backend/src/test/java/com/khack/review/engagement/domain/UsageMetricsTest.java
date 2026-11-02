package com.khack.review.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class UsageMetricsTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    static SortedSet<LocalDate> days(String... dates) {
        return new TreeSet<>(Arrays.stream(dates).map(LocalDate::parse).toList());
    }

    @Test
    void countsUsersWhoWentFromAConversationToTheirFirstAnswer() {
        UsageMetrics metrics = UsageMetrics.of(TODAY, Set.of(1L, 2L, 3L, 4L),
                Map.of(1L, days("2026-11-01"), 2L, days("2026-11-02")), 0);

        assertThat(metrics.usersWithConversation()).isEqualTo(4);
        assertThat(metrics.usersReachedPractice()).isEqualTo(2);
        assertThat(metrics.reachedPracticeRate()).isEqualTo(0.5);
    }

    @Test
    void returnRateCountsUsersWhoAnsweredOnTwoOrMoreDays() {
        UsageMetrics metrics = UsageMetrics.of(TODAY, Set.of(),
                Map.of(1L, days("2026-11-01", "2026-11-02"), 2L, days("2026-11-03")), 0);

        assertThat(metrics.returnedUsers()).isEqualTo(1);
        assertThat(metrics.returnRate()).isEqualTo(0.5);
    }

    @Test
    void retentionOnlyCountsUsersWhoseFirstAnswerIsOldEnough() {
        UsageMetrics metrics = UsageMetrics.of(TODAY, Set.of(), Map.of(
                1L, days("2026-11-01", "2026-11-09"),
                2L, days("2026-11-02", "2026-11-03"),
                3L, days("2026-11-08"),
                4L, days("2026-11-10")), 0);

        assertThat(metrics.day1()).isEqualTo(new UsageMetrics.Retention(1, 3, 2, 2 / 3.0));
        assertThat(metrics.day7()).as("첫 풀이가 11월 3일 이전인 사용자만 잰다")
                .isEqualTo(new UsageMetrics.Retention(7, 2, 1, 0.5));
    }

    @Test
    void ratesAreEmptyWithoutUsers() {
        UsageMetrics metrics = UsageMetrics.of(TODAY, Set.of(), Map.of(), 0);

        assertThat(metrics.reachedPracticeRate()).isNull();
        assertThat(metrics.returnRate()).isNull();
        assertThat(metrics.day7().rate()).isNull();
    }
}
