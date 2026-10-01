package com.khack.review.practice.application;

import com.khack.review.analysis.application.LearningSessionNotFoundException;
import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.memory.application.MemoryGaugeService;
import com.khack.review.memory.application.MemoryStateService;
import com.khack.review.memory.application.ReviewOutcomeQuery;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import io.github.openspacedrepetition.Rating;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 학습 완료 요약 (스펙 §7 17단계, 도메인 스토리 S1-17). 확인한 항목, 도움이 필요했던 항목, 아직 확인 전인 항목,
 * 다음 복습 일정과 기억 게이지를 모아 보여준다. 읽기 전용이며 R·복습 시각은 기억 컨텍스트가 계산한 값을 그대로 쓴다.
 * 결과 분류: 도움 없이 떠올렸으면(Hard 이상) 확인, 도움을 봤거나 Again·보류면 도움이 필요, 등급이 없으면 확인 전이다.
 */
@Service
public class FirstStudySummaryService {

    private final SessionItemsQuery sessionItems;
    private final MemoryGaugeService gauges;
    private final MemoryStateService memory;
    private final ReviewOutcomeQuery outcomes;
    private final PracticeSessionRepository practices;

    public FirstStudySummaryService(SessionItemsQuery sessionItems, MemoryGaugeService gauges, MemoryStateService memory,
            ReviewOutcomeQuery outcomes, PracticeSessionRepository practices) {
        this.sessionItems = sessionItems;
        this.gauges = gauges;
        this.memory = memory;
        this.outcomes = outcomes;
        this.practices = practices;
    }

    public record SummaryItem(Long memoryItemId, Long unitId, String unitTitle, MemoryItemKind kind, String content,
            MemoryGaugeService.ItemGauge gauge, @Nullable Instant nextReviewAt) {
    }

    /**
     * @param completed    첫 학습 풀이를 끝냈는가(풀이 세션이 완료됨)
     * @param nextReviewAt 항목들의 다음 복습 시각 중 가장 이른 것. 등급 받은 항목이 없으면 null
     */
    public record Summary(Long sessionId, boolean completed, @Nullable Instant completedAt, List<SummaryItem> confirmed,
            List<SummaryItem> needsHelp, List<SummaryItem> notChecked, @Nullable Instant nextReviewAt,
            List<MemoryGaugeService.UnitView> units) {
    }

    @Transactional(readOnly = true)
    public Summary summary(Long userId, Long sessionId) {
        List<SessionItemsQuery.UnitItems> units = sessionItems.activeUnits(userId, sessionId)
                .orElseThrow(() -> new LearningSessionNotFoundException(sessionId));
        Optional<PracticeSession> practice = practices.findFirstByLearningSessionIdAndKindOrderByIdAsc(sessionId,
                PracticeKind.FIRST_STUDY);
        Instant completedAt = practice.map(PracticeSession::getCompletedAt).orElse(null);
        Map<Long, MemoryGaugeService.ItemGauge> itemGauges = gauges.itemGauges(units.stream()
                .flatMap(unit -> unit.items().stream()).map(SessionItemsQuery.Item::memoryItemId).toList());

        List<SummaryItem> confirmed = new ArrayList<>();
        List<SummaryItem> needsHelp = new ArrayList<>();
        List<SummaryItem> notChecked = new ArrayList<>();
        List<MemoryGaugeService.UnitView> unitViews = new ArrayList<>();
        for (SessionItemsQuery.UnitItems unit : units) {
            List<MemoryGaugeService.ItemView> itemViews = new ArrayList<>();
            for (SessionItemsQuery.Item item : unit.items()) {
                MemoryGaugeService.ItemGauge gauge = itemGauges.get(item.memoryItemId());
                itemViews.add(new MemoryGaugeService.ItemView(item.memoryItemId(), item.kind(), item.content(), gauge));
                SummaryItem entry = new SummaryItem(item.memoryItemId(), unit.unitId(), unit.title(), item.kind(),
                        item.content(), gauge, memory.nextReviewAt(item.memoryItemId()).orElse(null));
                switch (classify(outcomes.outcome(item.memoryItemId()))) {
                    case CONFIRMED -> confirmed.add(entry);
                    case NEEDS_HELP -> needsHelp.add(entry);
                    case NOT_CHECKED -> notChecked.add(entry);
                }
            }
            unitViews.add(new MemoryGaugeService.UnitView(unit.unitId(), unit.title(),
                    MemoryGaugeService.unitGauge(itemViews.stream().map(MemoryGaugeService.ItemView::gauge).toList()), itemViews));
        }
        Instant next = Stream.of(confirmed, needsHelp, notChecked).flatMap(List::stream)
                .map(SummaryItem::nextReviewAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return new Summary(sessionId, completedAt != null, completedAt, confirmed, needsHelp, notChecked, next, unitViews);
    }

    private enum Bucket { CONFIRMED, NEEDS_HELP, NOT_CHECKED }

    private static Bucket classify(ReviewOutcomeQuery.Outcome outcome) {
        Rating rating = outcome.latestRating();
        if (rating == null && !outcome.held() && !outcome.aidExposed()) {
            return Bucket.NOT_CHECKED;
        }
        boolean weak = rating == Rating.AGAIN;
        if (outcome.held() || outcome.aidExposed() || weak) {
            return Bucket.NEEDS_HELP;
        }
        return rating == null ? Bucket.NOT_CHECKED : Bucket.CONFIRMED;
    }
}
