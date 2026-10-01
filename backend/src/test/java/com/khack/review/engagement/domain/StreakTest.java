package com.khack.review.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 연속 학습 일수 (스펙 §7.7). 기록은 오늘(마지막 글자)까지 하루 한 글자: C 완료, E 빈 날, . 기록 없음. */
class StreakTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    static List<DailyRecord> days(String pattern) {
        List<DailyRecord> records = new ArrayList<>();
        for (int i = 0; i < pattern.length(); i++) {
            LocalDate date = TODAY.minusDays(pattern.length() - 1 - i);
            switch (pattern.charAt(i)) {
                case 'C' -> records.add(new DailyRecord(1L, date, DailyStatus.COMPLETED));
                case 'E' -> records.add(new DailyRecord(1L, date, DailyStatus.EMPTY));
                default -> {
                }
            }
        }
        return records;
    }

    @ParameterizedTest(name = "{0} → 현재 {1}, 최고 {2}")
    @CsvSource({
            "'',      0, 0",
            "C,       1, 1",
            "CCC,     3, 3",
            "CC.,     2, 2",
            "CCE,     2, 2",
            "CECEC,   3, 3",
            "EEEC,    1, 1",
            "CC.C,    1, 2",
            "CCC..,   0, 3",
            "CC.CC.,  2, 2",
            "CCCC.CE, 1, 4",
            "C.E,     0, 1",
    })
    void countsCompletedDaysAndSkipsEmptyDays(String pattern, int current, int best) {
        Streak streak = Streak.of(days(pattern), TODAY);

        assertThat(streak.current()).isEqualTo(current);
        assertThat(streak.best()).isEqualTo(best);
    }

    @ParameterizedTest
    @CsvSource({"C, COMPLETED", "CE, EMPTY"})
    void reportsTodaysStatus(String pattern, DailyStatus today) {
        assertThat(Streak.of(days(pattern), TODAY).today()).isEqualTo(today);
    }

    @ParameterizedTest
    @CsvSource("CC.")
    void todayWithoutARecordIsStillOpen(String pattern) {
        assertThat(Streak.of(days(pattern), TODAY).today()).isNull();
        assertThat(Streak.of(days(pattern), TODAY.plusDays(1)).current()).as("다음 날이 되면 끊긴다").isZero();
    }
}
