package com.khack.review.memory.application;

import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.memory.domain.FsrsSchedulers;
import com.khack.review.memory.domain.MemoryStrength;
import com.khack.review.memory.domain.SessionMemorySettings;
import com.khack.review.memory.domain.SessionMemorySettingsRepository;
import com.khack.review.memory.domain.WorkloadSimulation;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 세션의 기억 강도 (스펙 §6.4.7). 강도별 목표 유지율·문제 유형 상한과 예상 하루 부담을 보여주고,
 * 고르면 세션 기억 항목의 목표 유지율을 바꾼다.
 */
@Service
public class MemoryStrengthService {

    private final SessionItemsQuery sessionItems;
    private final SessionMemorySettingsRepository settings;
    private final MemoryStateService memory;
    private final int simulationDays;
    private final double minutesPerReview;

    public MemoryStrengthService(SessionItemsQuery sessionItems, SessionMemorySettingsRepository settings,
            MemoryStateService memory,
            @Value("${review.memory.simulation-days:30}") int simulationDays,
            @Value("${review.memory.minutes-per-review:0.75}") double minutesPerReview) {
        this.sessionItems = sessionItems;
        this.settings = settings;
        this.memory = memory;
        this.simulationDays = simulationDays;
        this.minutesPerReview = minutesPerReview;
    }

    /** 기억 강도 1개의 선택지. {@code dailyMinutes}는 이 세션만 놓고 본 하루 평균 예상 시간이다. */
    public record Option(MemoryStrength strength, String label, double desiredRetention, int maxQuestionLevel,
            int reviewsInPeriod, double dailyMinutes) {
    }

    /** {@code current}는 아직 고르지 않았으면 null. */
    public record Options(@Nullable MemoryStrength current, int itemCount, int simulationDays, List<Option> options) {
    }

    @Transactional(readOnly = true)
    public Options options(Long userId, Long sessionId) {
        int itemCount = itemIds(userId, sessionId).size();
        MemoryStrength current = settings.findById(sessionId).map(SessionMemorySettings::getStrength).orElse(null);
        List<Option> options = Arrays.stream(MemoryStrength.values()).map(strength -> {
            WorkloadSimulation.Estimate estimate = WorkloadSimulation.estimate(
                    FsrsSchedulers.create(FsrsSchedulers.defaultParameters(), strength.desiredRetention()),
                    itemCount, simulationDays, minutesPerReview);
            return new Option(strength, strength.label(), strength.desiredRetention(), strength.maxQuestionLevel(),
                    estimate.reviews(), estimate.dailyMinutes());
        }).toList();
        return new Options(current, itemCount, simulationDays, options);
    }

    @Transactional
    public void choose(Long userId, Long sessionId, MemoryStrength strength) {
        List<Long> items = itemIds(userId, sessionId);
        settings.findById(sessionId).ifPresentOrElse(
                existing -> existing.change(strength),
                () -> settings.save(new SessionMemorySettings(sessionId, userId, strength)));
        memory.applyRetention(userId, items, strength.desiredRetention());
    }

    private List<Long> itemIds(Long userId, Long sessionId) {
        return sessionItems.activeItemIds(userId, sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
    }
}
