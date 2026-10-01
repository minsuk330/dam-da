package com.khack.review.memory.application;

import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.analysis.domain.MemoryItemKind;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기억 게이지 (스펙 §6.4.3). 항목별 현재 R을 보여주고, 복습 단위는 소속 항목 R의 평균과 가장 약한 항목을 함께 보여준다.
 * R은 읽을 때마다 주입된 {@code Clock}으로 계산하므로 시간 이동(§11.3)하면 바로 떨어진다. 아무것도 쓰지 않는다.
 * 아직 등급을 받지 않은 항목은 R이 없고("아직 확인 전") 단위 평균에서 빠진다.
 */
@Service
public class MemoryGaugeService {

    private final SessionItemsQuery sessionItems;
    private final MemoryStateService memory;

    public MemoryGaugeService(SessionItemsQuery sessionItems, MemoryStateService memory) {
        this.sessionItems = sessionItems;
        this.memory = memory;
    }

    /** 항목 게이지. {@code retrievability}·{@code percent}가 null이면 "아직 확인 전"이다. */
    public record ItemGauge(Long memoryItemId, boolean checked, @Nullable Double retrievability, @Nullable Integer percent) {

        static ItemGauge of(Long memoryItemId, Optional<Double> retrievability) {
            return new ItemGauge(memoryItemId, retrievability.isPresent(), retrievability.orElse(null),
                    retrievability.map(r -> (int) Math.round(r * 100)).orElse(null));
        }
    }

    /**
     * 복습 단위 게이지. {@code average}는 확인된 항목 R의 평균, {@code weakestItemId}는 그중 R이 가장 낮은 항목이다.
     * 확인된 항목이 없으면 둘 다 null이다.
     */
    public record UnitGauge(@Nullable Double average, @Nullable Integer averagePercent, @Nullable Long weakestItemId,
            @Nullable Integer weakestPercent, int checkedItems, int totalItems) {
    }

    public record ItemView(Long memoryItemId, MemoryItemKind kind, String content, ItemGauge gauge) {
    }

    public record UnitView(Long unitId, String title, UnitGauge gauge, List<ItemView> items) {
    }

    public record SessionGauge(Long sessionId, List<UnitView> units) {
    }

    /** 항목 ID별 게이지. 지금 시각 기준이다. */
    @Transactional(readOnly = true)
    public Map<Long, ItemGauge> itemGauges(List<Long> memoryItemIds) {
        return memoryItemIds.stream().collect(Collectors.toMap(Function.identity(),
                id -> ItemGauge.of(id, memory.retrievability(id)), (first, later) -> first));
    }

    /** 항목 게이지들을 한 복습 단위로 모은다. */
    public static UnitGauge unitGauge(List<ItemGauge> items) {
        List<ItemGauge> checked = items.stream().filter(ItemGauge::checked).toList();
        if (checked.isEmpty()) {
            return new UnitGauge(null, null, null, null, 0, items.size());
        }
        double average = checked.stream().mapToDouble(ItemGauge::retrievability).average().orElseThrow();
        ItemGauge weakest = checked.stream().min(Comparator.comparingDouble(ItemGauge::retrievability)).orElseThrow();
        return new UnitGauge(average, (int) Math.round(average * 100), weakest.memoryItemId(), weakest.percent(),
                checked.size(), items.size());
    }

    /** 세션의 복습 단위별 게이지. 사용자의 세션이 아니면 {@link SessionNotFoundException}. */
    @Transactional(readOnly = true)
    public SessionGauge sessionGauge(Long userId, Long sessionId) {
        List<SessionItemsQuery.UnitItems> units = sessionItems.activeUnits(userId, sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        Map<Long, ItemGauge> gauges = itemGauges(units.stream()
                .flatMap(unit -> unit.items().stream()).map(SessionItemsQuery.Item::memoryItemId).toList());
        return new SessionGauge(sessionId, units.stream().map(unit -> {
            List<ItemView> items = unit.items().stream()
                    .map(item -> new ItemView(item.memoryItemId(), item.kind(), item.content(), gauges.get(item.memoryItemId())))
                    .toList();
            return new UnitView(unit.unitId(), unit.title(),
                    unitGauge(items.stream().map(ItemView::gauge).toList()), items);
        }).toList());
    }
}
