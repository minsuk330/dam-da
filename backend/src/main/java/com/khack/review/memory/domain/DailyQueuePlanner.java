package com.khack.review.memory.domain;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 매일 학습 큐 계획 (스펙 §6.4.4). 후보와 정책을 받아 순서와 예산이 정해진 큐를 돌려주는 순수 계산이다.
 *
 * <ol>
 *   <li>복습 후보는 R이 목표 유지율 아래이거나, 보류이거나, 당일 재학습인 항목이다.
 *       우선순위 = 긴급도 × 가중치. 긴급도는 1 − R이고, 당일 재학습은 1, 보류는 최소 {@code heldMinUrgency}다.</li>
 *   <li>같은 복습 단위는 우선순위가 가장 높은 항목 1개만 남긴다. 오늘 이미 그 단위를 푼 경우({@code servedUnitIds})에도 뺀다.
 *       단, 당일 재학습 항목은 오늘 푼 자기 단위여도 허용한다.</li>
 *   <li>우선순위 순으로 시간 예산까지 채운다. 다음 항목이 넘치면 거기서 자르고 나머지는 다음 큐로 넘긴다(건너뛰고 뒤 항목을 채우지 않는다).
 *       첫 항목은 예산을 넘어도 낸다.</li>
 *   <li>복습을 먼저 채운 뒤 남은 예산 안에서 신규 항목을 하루 {@code maxNewItems}개까지 붙인다. 신규 항목에도 2를 적용한다.</li>
 * </ol>
 * 소요 시간은 문제 유형별 기준 시간의 합이다.
 */
public final class DailyQueuePlanner {

    private DailyQueuePlanner() {
    }

    /** 정책. {@code weights}에 없는 종류의 가중치는 1이다. */
    public record Policy(Duration budget, int maxNewItems, Map<MemoryItemKind, Double> weights, double heldMinUrgency,
            Map<QuestionType, Duration> referenceTimes) {

        double weight(MemoryItemKind kind) {
            return weights.getOrDefault(kind, 1.0);
        }

        Duration cost(QuestionType type) {
            Duration time = referenceTimes.get(type);
            if (time == null) {
                throw new IllegalArgumentException("문제 유형 %s의 기준 시간이 없습니다.".formatted(type));
            }
            return time;
        }
    }

    /**
     * 복습 후보. {@code retrievability}는 아직 등급을 받은 적 없으면 null이다.
     * {@code type}은 안정도 사다리로 미리 정한 문제 유형이다. {@code avoidQuestionId}는 모호해서 보류된 문제로, 같은 문제를 다시 내지 않는다.
     */
    public record ReviewCandidate(Long itemId, Long unitId, Long sessionId, MemoryItemKind kind,
            @Nullable Double retrievability, double desiredRetention, boolean held, boolean relearnToday, QuestionType type,
            @Nullable Long avoidQuestionId) {

        boolean due() {
            return retrievability != null && retrievability < desiredRetention;
        }

        boolean eligible() {
            return due() || held || relearnToday;
        }
    }

    /** 신규 후보(첫 풀이 전). {@code type}은 세션의 학습 목표로 정한다. */
    public record NewCandidate(Long itemId, Long unitId, Long sessionId, MemoryItemKind kind, QuestionType type) {
    }

    public enum Source {
        REVIEW, NEW
    }

    public enum CarryReason {
        /** 시간 예산을 넘는다. */
        BUDGET,
        /** 같은 복습 단위의 다른 항목이 오늘 이미 있다(큐에 들었거나 오늘 풀었다). */
        SAME_UNIT,
        /** 신규 항목 하루 상한을 넘는다. */
        NEW_LIMIT
    }

    public record Entry(Long itemId, Long unitId, Long sessionId, MemoryItemKind kind, QuestionType type, Source source,
            double priority, @Nullable Double retrievability, boolean held, boolean relearnToday, Duration estimated,
            @Nullable Long avoidQuestionId) {
    }

    public record Carried(Long itemId, CarryReason reason) {
    }

    public record Plan(List<Entry> entries, List<Carried> carriedOver, Duration estimatedTime, int reviewCount, int newCount) {
    }

    public static Plan plan(List<ReviewCandidate> reviews, List<NewCandidate> news, Set<Long> servedUnitIds, Policy policy) {
        List<Carried> carried = new ArrayList<>();
        Set<Long> queuedUnits = new HashSet<>();
        List<Entry> entries = new ArrayList<>();
        Duration used = Duration.ZERO;

        List<Entry> ranked = reviews.stream().filter(ReviewCandidate::eligible)
                .map(c -> reviewEntry(c, policy))
                .sorted(Comparator.comparingDouble(Entry::priority).reversed()
                        .thenComparing(Entry::relearnToday, Comparator.reverseOrder())
                        .thenComparing(Entry::itemId))
                .toList();
        boolean full = false;
        for (Entry entry : ranked) {
            boolean servedBlock = servedUnitIds.contains(entry.unitId()) && !entry.relearnToday();
            if (servedBlock || queuedUnits.contains(entry.unitId())) {
                carried.add(new Carried(entry.itemId(), CarryReason.SAME_UNIT));
                continue;
            }
            queuedUnits.add(entry.unitId());
            if (full || (!entries.isEmpty() && used.plus(entry.estimated()).compareTo(policy.budget()) > 0)) {
                full = true;
                carried.add(new Carried(entry.itemId(), CarryReason.BUDGET));
                continue;
            }
            entries.add(entry);
            used = used.plus(entry.estimated());
        }
        int reviewCount = entries.size();

        List<NewCandidate> orderedNew = news.stream()
                .sorted(Comparator.comparingDouble((NewCandidate c) -> policy.weight(c.kind())).reversed()
                        .thenComparing(NewCandidate::sessionId).thenComparing(NewCandidate::itemId))
                .toList();
        int newCount = 0;
        boolean newClosed = false;
        for (NewCandidate candidate : orderedNew) {
            if (servedUnitIds.contains(candidate.unitId()) || queuedUnits.contains(candidate.unitId())) {
                carried.add(new Carried(candidate.itemId(), CarryReason.SAME_UNIT));
                continue;
            }
            if (newCount >= policy.maxNewItems()) {
                carried.add(new Carried(candidate.itemId(), CarryReason.NEW_LIMIT));
                continue;
            }
            Duration cost = policy.cost(candidate.type());
            if (newClosed || used.plus(cost).compareTo(policy.budget()) > 0) {
                newClosed = true;
                carried.add(new Carried(candidate.itemId(), CarryReason.BUDGET));
                continue;
            }
            queuedUnits.add(candidate.unitId());
            entries.add(new Entry(candidate.itemId(), candidate.unitId(), candidate.sessionId(), candidate.kind(), candidate.type(),
                    Source.NEW, 0, null, false, false, cost, null));
            used = used.plus(cost);
            newCount++;
        }
        return new Plan(List.copyOf(entries), List.copyOf(carried), used, reviewCount, newCount);
    }

    private static Entry reviewEntry(ReviewCandidate c, Policy policy) {
        double urgency = c.relearnToday() ? 1.0 : c.due() ? 1.0 - c.retrievability() : 0.0;
        if (c.held()) {
            urgency = Math.max(urgency, policy.heldMinUrgency());
        }
        return new Entry(c.itemId(), c.unitId(), c.sessionId(), c.kind(), c.type(), Source.REVIEW,
                urgency * policy.weight(c.kind()), c.retrievability(), c.held(), c.relearnToday(), policy.cost(c.type()),
                c.avoidQuestionId());
    }
}
