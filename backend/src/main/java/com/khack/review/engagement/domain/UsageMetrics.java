package com.khack.review.engagement.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import org.jspecify.annotations.Nullable;

/**
 * 사용 지표(스펙 §12.1, #182). 기존 기록(대화 저장, 답 제출, 매일 학습 결과)만으로 계산한다. 대화 내용은 쓰지 않는다.
 *
 * @param usersWithConversation 대화를 한 번이라도 저장한 사용자 수
 * @param usersReachedPractice  그중 답을 한 번이라도 낸 사용자 수
 * @param reachedPracticeRate   대화 등록 뒤 첫 풀이까지 간 비율
 * @param usersWithAttempt      답을 한 번이라도 낸 사용자 수
 * @param returnedUsers         풀이한 날이 이틀 이상인 사용자 수
 * @param returnRate            재방문율: returnedUsers / usersWithAttempt
 * @param day1                  첫 풀이 다음 날 이후에 다시 푼 비율
 * @param day7                  첫 풀이 7일 뒤 이후에 다시 푼 비율
 * @param dailyCompletedLast7Days 최근 7일(오늘 포함) 매일 학습을 끝낸 횟수
 */
public record UsageMetrics(LocalDate today, int usersWithConversation, int usersReachedPractice, @Nullable Double reachedPracticeRate,
        int usersWithAttempt, int returnedUsers, @Nullable Double returnRate, Retention day1, Retention day7,
        long dailyCompletedLast7Days) {

    /**
     * @param eligible 첫 풀이가 N일 이전이라 잴 수 있는 사용자 수
     * @param returned 그중 첫 풀이 N일 뒤 이후에 다시 푼 사용자 수
     */
    public record Retention(int days, int eligible, int returned, @Nullable Double rate) {
    }

    /** {@code attemptDays}: 사용자별 답을 낸 날(서버 시간대). */
    public static UsageMetrics of(LocalDate today, Set<Long> conversationUsers, Map<Long, SortedSet<LocalDate>> attemptDays,
            long dailyCompletedLast7Days) {
        int reached = (int) conversationUsers.stream().filter(attemptDays::containsKey).count();
        int returned = (int) attemptDays.values().stream().filter(days -> days.size() >= 2).count();
        return new UsageMetrics(today, conversationUsers.size(), reached, rate(reached, conversationUsers.size()),
                attemptDays.size(), returned, rate(returned, attemptDays.size()),
                retention(1, today, attemptDays), retention(7, today, attemptDays), dailyCompletedLast7Days);
    }

    private static Retention retention(int days, LocalDate today, Map<Long, SortedSet<LocalDate>> attemptDays) {
        int eligible = 0;
        int returned = 0;
        for (SortedSet<LocalDate> userDays : attemptDays.values()) {
            LocalDate target = userDays.first().plusDays(days);
            if (target.isAfter(today)) {
                continue;
            }
            eligible++;
            if (!userDays.tailSet(target).isEmpty()) {
                returned++;
            }
        }
        return new Retention(days, eligible, returned, rate(returned, eligible));
    }

    private static @Nullable Double rate(int part, int whole) {
        return whole == 0 ? null : (double) part / whole;
    }
}
