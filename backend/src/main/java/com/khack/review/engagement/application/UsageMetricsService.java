package com.khack.review.engagement.application;

import com.khack.review.collection.application.ConversationQueryService;
import com.khack.review.engagement.domain.DailyRecordRepository;
import com.khack.review.engagement.domain.DailyStatus;
import com.khack.review.engagement.domain.UsageMetrics;
import com.khack.review.practice.application.PracticeActivityQueryService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전체 사용자 기준 사용 지표(스펙 §12.1, #182). 개발 도구({@code /dev/metrics})에서만 읽는다. */
@Service
public class UsageMetricsService {

    private final ConversationQueryService conversations;
    private final PracticeActivityQueryService practice;
    private final DailyRecordRepository dailyRecords;
    private final Clock clock;

    public UsageMetricsService(ConversationQueryService conversations, PracticeActivityQueryService practice,
            DailyRecordRepository dailyRecords, Clock clock) {
        this.conversations = conversations;
        this.practice = practice;
        this.dailyRecords = dailyRecords;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsageMetrics current() {
        LocalDate today = LocalDate.now(clock);
        long completed = dailyRecords.countByStatusAndDateGreaterThanEqual(DailyStatus.COMPLETED, today.minusDays(6));
        return UsageMetrics.of(today, conversations.usersWithConversations(), practice.attemptDaysByUser(), completed);
    }
}
