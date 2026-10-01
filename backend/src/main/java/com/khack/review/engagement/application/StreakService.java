package com.khack.review.engagement.application;

import com.khack.review.engagement.domain.DailyRecord;
import com.khack.review.engagement.domain.DailyRecordRepository;
import com.khack.review.engagement.domain.DailyStatus;
import com.khack.review.engagement.domain.Streak;
import com.khack.review.engagement.domain.StreakUpdated;
import com.khack.review.memory.domain.DailyQueueBuilt;
import com.khack.review.practice.application.DailyPracticeService;
import com.khack.review.practice.domain.DailyPracticeCompleted;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연속 학습 일수 (스펙 §7.7, 도메인 스토리 S2-9). 그날 큐를 끝내면 완료, 복습할 항목이 없어 큐가 비면 빈 날로 기록하고,
 * 일수는 기록으로 매번 계산한다. 못 끝낸 날의 항목은 매일 학습 큐가 다음 날 다시 고르므로(미완료 복습 이월) 여기서는 기록만 끊긴다.
 */
@Service
public class StreakService {

    private final DailyRecordRepository records;
    private final DailyPracticeService dailyPractice;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StreakService(DailyRecordRepository records, DailyPracticeService dailyPractice, ApplicationEventPublisher events,
            Clock clock) {
        this.records = records;
        this.dailyPractice = dailyPractice;
        this.events = events;
        this.clock = clock;
    }

    /** 풀이 완료와 같은 트랜잭션에서 그날을 완료로 기록한다. */
    @EventListener
    @Transactional
    public void on(DailyPracticeCompleted event) {
        records.findByUserIdAndDate(event.userId(), event.date()).ifPresentOrElse(DailyRecord::complete,
                () -> records.save(new DailyRecord(event.userId(), event.date(), DailyStatus.COMPLETED)));
        Streak streak = Streak.of(records.findByUserIdOrderByDateAsc(event.userId()), today());
        events.publishEvent(new StreakUpdated(event.userId(), event.date(), streak.current(), streak.best()));
    }

    /** 오늘의 학습을 시작하려는데 큐가 비었다. */
    @EventListener
    @Transactional
    public void on(DailyQueueBuilt event) {
        if (event.itemIds().isEmpty()) {
            markEmpty(event.userId(), event.date());
        }
    }

    /** 복습할 항목이 없는 날. 이미 기록이 있으면 그대로 둔다. */
    @Transactional
    public void markEmpty(Long userId, LocalDate date) {
        if (records.findByUserIdAndDate(userId, date).isEmpty()) {
            records.save(new DailyRecord(userId, date, DailyStatus.EMPTY));
        }
    }

    /** 지금 연속 일수. 오늘 기록이 없고 오늘의 큐가 비어 있으면 빈 날로 남긴 뒤 계산한다. */
    @Transactional
    public Streak streak(Long userId) {
        LocalDate today = today();
        if (records.findByUserIdAndDate(userId, today).isEmpty()) {
            DailyPracticeService.DailyView view = dailyPractice.today(userId);
            if (!view.started() && view.total() == 0) {
                markEmpty(userId, today);
            }
        }
        return Streak.of(records.findByUserIdOrderByDateAsc(userId), today);
    }

    private LocalDate today() {
        return clock.instant().atZone(clock.getZone()).toLocalDate();
    }
}
