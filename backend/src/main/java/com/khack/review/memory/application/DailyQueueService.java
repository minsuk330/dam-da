package com.khack.review.memory.application;

import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.analysis.application.SessionItemsQuery.ActiveItem;
import com.khack.review.memory.domain.DailyQueueBuilt;
import com.khack.review.memory.domain.DailyQueuePlanner;
import com.khack.review.memory.domain.MemoryState;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.MemoryStrength;
import com.khack.review.memory.domain.NewItems;
import com.khack.review.memory.domain.QuestionLadder;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import com.khack.review.memory.domain.SessionMemorySettings;
import com.khack.review.memory.domain.SessionMemorySettingsRepository;
import io.github.openspacedrepetition.Rating;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 매일 학습 큐를 만든다 (스펙 §6.4.4, §6.4.6, 도메인 스토리 S2-2~4). 모든 세션의 확인된 기억 항목에서 후보를 모으고
 * 복습 항목의 문제 유형은 안정도 사다리로 정한 뒤, 순서·예산·하루 1개·신규 분리는 {@link DailyQueuePlanner}가 정한다.
 * 문제를 고르거나 만드는 일, 풀이 세션 생성은 여기서 하지 않는다(practice).
 *
 * <p>후보 규칙:
 * <ul>
 *   <li>첫 풀이 전(복습 기록이 없는) 항목은 신규다. 복습 후보에 넣지 않는다. 첫 학습을 끝낸 세션의 항목만 신규로 받는다
 *       ({@link NewItems#admits}). 첫 학습 전·도중인 세션의 항목은 첫 학습에서 다룬다.</li>
 *   <li>첫 풀이 이후 항목은 R이 목표 유지율 아래이거나, 보류({@code consecutiveHolds > 0})이거나, 당일 재학습이면 복습 후보다.</li>
 *   <li>연속 보류로 자동 출제에서 빠진 항목({@code autoQuestionsPaused})은 후보에서 뺀다.</li>
 *   <li><b>당일 재학습</b>: 오늘(시계 기준 날짜) 가장 최근 복습 기록이 Again인 항목, 그리고 호출자가 넘긴 {@code extraRelearnToday}.
 *       오늘 안에 다시 맞히면 최근 기록이 바뀌어 후보에서 빠진다. 단계적 피드백(#20)이 {@code relearn_today}를 고른 항목은
 *       {@code extraRelearnToday}로 넘기면 같은 방식으로 편성된다.</li>
 *   <li>오늘 이미 복습 기록이 있는 단위는 하루 1개 규칙으로 다른 항목을 내지 않는다(당일 재학습 항목은 예외).</li>
 * </ul>
 */
@Service
public class DailyQueueService {

    private static final int DEFAULT_MAX_LEVEL = MemoryStrength.APPLY.maxQuestionLevel();

    private final SessionItemsQuery sessionItems;
    private final MemoryStateRepository states;
    private final ReviewLogRepository logs;
    private final SessionMemorySettingsRepository settings;
    private final FsrsParametersService parameters;
    private final RatingPolicyProperties rating;
    private final DailyQueueProperties properties;
    private final StudySettingsService studySettings;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public DailyQueueService(SessionItemsQuery sessionItems, MemoryStateRepository states, ReviewLogRepository logs,
            SessionMemorySettingsRepository settings, FsrsParametersService parameters, RatingPolicyProperties rating,
            DailyQueueProperties properties, Clock clock, ApplicationEventPublisher events, StudySettingsService studySettings) {
        this.sessionItems = sessionItems;
        this.states = states;
        this.logs = logs;
        this.settings = settings;
        this.parameters = parameters;
        this.rating = rating;
        this.properties = properties;
        this.studySettings = studySettings;
        this.clock = clock;
        this.events = events;
    }

    /** 오늘({@code date}) 만든 큐. 큐 순서는 복습(우선순위 순) 다음 신규다. */
    public record DailyQueue(LocalDate date, DailyQueuePlanner.Plan plan) {
    }

    /** 이벤트 없이 오늘의 큐를 계산한다(조회용). */
    @Transactional(readOnly = true)
    public DailyQueue preview(Long userId, NewItems newItems, Set<Long> extraRelearnToday) {
        return compute(userId, newItems, extraRelearnToday);
    }

    /** 오늘의 큐를 만들고 {@link DailyQueueBuilt}를 발행한다(시작용). */
    @Transactional
    public DailyQueue build(Long userId, NewItems newItems, Set<Long> extraRelearnToday) {
        DailyQueue queue = compute(userId, newItems, extraRelearnToday);
        DailyQueuePlanner.Plan plan = queue.plan();
        events.publishEvent(new DailyQueueBuilt(userId, queue.date(), plan.entries().stream().map(DailyQueuePlanner.Entry::itemId).toList(),
                plan.reviewCount(), plan.newCount(), plan.estimatedTime(), plan.carriedOver().size()));
        return queue;
    }

    private DailyQueue compute(Long userId, NewItems newItems, Set<Long> extraRelearnToday) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(clock.getZone()).toLocalDate();
        Instant dayStart = today.atStartOfDay(clock.getZone()).toInstant();

        List<ActiveItem> items = sessionItems.activeItemsOf(userId);
        Map<Long, MemoryState> stateByItem = states.findByUserId(userId).stream()
                .collect(Collectors.toMap(MemoryState::getMemoryItemId, Function.identity(), (a, b) -> a));
        Map<Long, Integer> maxLevelBySession = settings.findByUserId(userId).stream()
                .collect(Collectors.toMap(SessionMemorySettings::getSessionId, s -> s.getStrength().maxQuestionLevel()));

        // 복습 기록(등급 또는 보류)을 항목별 최신순으로 훑는다.
        Map<Long, ReviewLog> latest = new HashMap<>();
        Map<Long, ReviewLog> latestToday = new HashMap<>();
        for (ReviewLog log : logs.findByUserIdOrderByReviewedAtAscIdAsc(userId)) {
            latest.put(log.getMemoryItemId(), log);
            if (!log.getReviewedAt().isBefore(dayStart)) {
                latestToday.put(log.getMemoryItemId(), log);
            }
        }

        Map<Long, Long> unitByItem = items.stream().collect(Collectors.toMap(ActiveItem::memoryItemId, ActiveItem::unitId));
        Set<Long> servedUnits = new HashSet<>();
        latestToday.keySet().forEach(itemId -> {
            Long unit = unitByItem.get(itemId);
            if (unit != null) {
                servedUnits.add(unit);
            }
        });
        Set<Long> relearn = new HashSet<>(extraRelearnToday);
        latestToday.forEach((itemId, log) -> {
            if (log.getRating() == Rating.AGAIN) {
                relearn.add(itemId);
            }
        });

        QuestionLadder ladder = properties.ladder();
        List<DailyQueuePlanner.ReviewCandidate> reviews = new ArrayList<>();
        List<DailyQueuePlanner.NewCandidate> news = new ArrayList<>();
        for (ActiveItem item : items) {
            MemoryState state = stateByItem.get(item.memoryItemId());
            if (state != null && state.isAutoQuestionsPaused()) {
                continue;
            }
            ReviewLog last = latest.get(item.memoryItemId());
            if (last == null) {
                if (!newItems.admits(item.sessionId())) {
                    continue;
                }
                news.add(new DailyQueuePlanner.NewCandidate(item.memoryItemId(), item.unitId(), item.sessionId(), item.kind(),
                        newItems.typeFor(item.sessionId(), item.memoryItemId(), item.kind())));
                continue;
            }
            Double retrievability = state == null ? null : state.retrievability(
                    parameters.activeFor(userId, state.getDesiredRetention()).scheduler(), now).orElse(null);
            double retention = state == null ? MemoryStrength.APPLY.desiredRetention() : state.getDesiredRetention();
            int maxLevel = maxLevelBySession.getOrDefault(item.sessionId(), DEFAULT_MAX_LEVEL);
            boolean held = state != null && state.getConsecutiveHolds() > 0;
            Long avoid = last.getHoldReason() != null && last.getHoldReason().questionNeedsRecheck() ? last.getQuestionId() : null;
            reviews.add(new DailyQueuePlanner.ReviewCandidate(item.memoryItemId(), item.unitId(), item.sessionId(), item.kind(),
                    retrievability, retention, held, relearn.contains(item.memoryItemId()),
                    ladder.typeFor(state == null ? null : state.getStability(), item.kind(), maxLevel), avoid));
        }
        return new DailyQueue(today, DailyQueuePlanner.plan(reviews, news, servedUnits, properties.policy(rating.referenceTimes(), studySettings.budget(userId))));
    }
}
