package com.khack.review.engagement.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 연속 학습 일수 (스펙 §7.7). 큐를 끝낸 날은 1일로 세고, 큐가 빈 날은 세지도 끊지도 않는다. 기록 없이 지나간 날이 있으면 끊긴다.
 * 오늘은 아직 끝나지 않았으므로 기록이 없어도 끊지 않는다. 날짜별 기록으로 매번 계산해 시간 이동에도 그대로 맞는다.
 *
 * @param current 오늘까지 이어진 연속 일수
 * @param best    지금까지 가장 긴 연속 일수
 * @param today   오늘의 기록. 없으면 null
 */
public record Streak(int current, int best, @Nullable DailyStatus today) {

    public static Streak of(Collection<DailyRecord> records, LocalDate today) {
        Map<LocalDate, DailyStatus> byDate = records.stream()
                .filter(record -> !record.getDate().isAfter(today))
                .collect(Collectors.toMap(DailyRecord::getDate, DailyRecord::getStatus, (a, b) -> a));
        if (byDate.isEmpty()) {
            return new Streak(0, 0, null);
        }
        LocalDate first = byDate.keySet().stream().min(LocalDate::compareTo).orElseThrow();

        int current = 0;
        for (LocalDate day = today; !day.isBefore(first); day = day.minusDays(1)) {
            DailyStatus status = byDate.get(day);
            if (status == DailyStatus.COMPLETED) {
                current++;
            } else if (status == null && !day.equals(today)) {
                break;
            }
        }

        int best = 0;
        int run = 0;
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            DailyStatus status = byDate.get(day);
            if (status == DailyStatus.COMPLETED) {
                run++;
            } else if (status == null && !day.equals(today)) {
                run = 0;
            }
            best = Math.max(best, run);
        }
        return new Streak(current, best, byDate.get(today));
    }
}
