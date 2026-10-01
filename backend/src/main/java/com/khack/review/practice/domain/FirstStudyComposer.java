package com.khack.review.practice.domain;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 학습 목표로 첫 학습 문제를 구성한다 (스펙 §7.8 표). 모든 문제는 기억 항목 하나를 대상으로 한다(§6.4.1).
 * 첫 학습에는 문제 유형 사다리(§6.4.6)를 적용하지 않는다. 상한을 넘으면 헷갈린 지점 문제를 먼저 남기고,
 * 문제에 들지 못한 항목은 신규 항목으로 남아 매일 학습에 편성된다(§6.4.4).
 */
public final class FirstStudyComposer {

    private FirstStudyComposer() {
    }

    /** 제외되지 않은 복습 단위와 기억 항목. 항목 순서는 세션에 저장된 순서다. */
    public record Unit(Long unitId, List<Item> items) {

        /** 대표 핵심 사실: 첫 `fact`, 없으면 첫 핵심 사실(헷갈린 지점 제외). */
        Optional<Item> representative() {
            return items.stream().filter(i -> i.kind() == MemoryItemKind.FACT).findFirst()
                    .or(() -> keyPoints().stream().findFirst());
        }

        List<Item> keyPoints() {
            return items.stream().filter(i -> i.kind() != MemoryItemKind.CONFUSION).toList();
        }
    }

    public record Item(Long id, MemoryItemKind kind) {
    }

    /** 계획된 문제 1개. {@code relatedItemId}는 개념 구분하기의 비교 대상이다. */
    public record PlannedQuestion(LearningGoal goal, Long memoryItemId, @Nullable Long relatedItemId, QuestionType type) {
    }

    /** 목표별 결과. {@code reason}은 대상 항목이 없어 0문제일 때만 있다. */
    public record GoalSummary(LearningGoal goal, int candidates, int planned, @Nullable String reason) {
    }

    public record Composition(List<PlannedQuestion> questions, List<GoalSummary> goals, List<Long> unplannedItemIds, int maxQuestions) {
    }

    public static Composition compose(List<LearningGoal> goals, List<Unit> units, int maxQuestions) {
        List<PlannedQuestion> candidates = new ArrayList<>();
        for (LearningGoal goal : goals) {
            candidates.addAll(candidates(goal, units));
        }
        List<PlannedQuestion> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing((PlannedQuestion q) -> kindOf(q.memoryItemId(), units) != MemoryItemKind.CONFUSION)
                .thenComparing(q -> goals.indexOf(q.goal())));
        List<PlannedQuestion> planned = List.copyOf(ordered.subList(0, Math.min(maxQuestions, ordered.size())));

        List<GoalSummary> summaries = goals.stream().map(goal -> {
            int candidateCount = (int) candidates.stream().filter(q -> q.goal() == goal).count();
            int plannedCount = (int) planned.stream().filter(q -> q.goal() == goal).count();
            return new GoalSummary(goal, candidateCount, plannedCount, candidateCount == 0 ? noTargetReason(goal) : null);
        }).toList();

        Set<Long> covered = new LinkedHashSet<>();
        planned.forEach(q -> {
            covered.add(q.memoryItemId());
            if (q.relatedItemId() != null) {
                covered.add(q.relatedItemId());
            }
        });
        List<Long> unplanned = units.stream().flatMap(u -> u.items().stream()).map(Item::id)
                .filter(id -> !covered.contains(id)).toList();
        return new Composition(planned, summaries, unplanned, maxQuestions);
    }

    private static List<PlannedQuestion> candidates(LearningGoal goal, List<Unit> units) {
        List<PlannedQuestion> out = new ArrayList<>();
        for (Unit unit : units) {
            switch (goal) {
                case KEY_RECALL -> unit.items().stream().filter(i -> i.kind() == MemoryItemKind.FACT)
                        .forEach(i -> out.add(new PlannedQuestion(goal, i.id(), null, QuestionType.SHORT_ANSWER)));
                case PRINCIPLE -> unit.representative()
                        .ifPresent(i -> out.add(new PlannedQuestion(goal, i.id(), null, QuestionType.ESSAY)));
                case DISTINGUISH -> {
                    List<Item> points = unit.keyPoints();
                    if (points.size() >= 2) {
                        Item first = unit.representative().orElseThrow();
                        Item other = points.stream().filter(i -> !i.id().equals(first.id())).findFirst().orElseThrow();
                        out.add(new PlannedQuestion(goal, first.id(), other.id(), QuestionType.MULTIPLE_CHOICE));
                    }
                }
                case CONDITION -> unit.items().stream().filter(i -> i.kind() == MemoryItemKind.WARNING)
                        .forEach(i -> out.add(new PlannedQuestion(goal, i.id(), null, QuestionType.CASE_JUDGMENT)));
                case APPLY_CASE -> unit.items().stream().filter(i -> i.kind() == MemoryItemKind.PRACTICE).findFirst()
                        .or(unit::representative)
                        .ifPresent(i -> out.add(new PlannedQuestion(goal, i.id(), null, QuestionType.CASE_APPLICATION)));
                case CORRECT_MISCONCEPTION -> unit.items().stream().filter(i -> i.kind() == MemoryItemKind.CONFUSION)
                        .forEach(i -> out.add(new PlannedQuestion(goal, i.id(), null, QuestionType.ERROR_FINDING)));
                case EXPLAIN_OWN_WORDS -> unit.representative()
                        .ifPresent(i -> out.add(new PlannedQuestion(goal, i.id(), null, QuestionType.ESSAY)));
            }
        }
        return out;
    }

    private static @Nullable MemoryItemKind kindOf(Long itemId, List<Unit> units) {
        return units.stream().flatMap(u -> u.items().stream()).filter(i -> i.id().equals(itemId))
                .map(Item::kind).findFirst().orElse(null);
    }

    private static String noTargetReason(LearningGoal goal) {
        return switch (goal) {
            case KEY_RECALL -> "개념 설명(fact) 핵심 사실이 없어 문제를 만들지 않습니다.";
            case PRINCIPLE, EXPLAIN_OWN_WORDS, APPLY_CASE -> "핵심 사실이 없어 문제를 만들지 않습니다.";
            case DISTINGUISH -> "비교할 핵심 사실이 2개 이상인 복습 단위가 없어 문제를 만들지 않습니다.";
            case CONDITION -> "주의(warning) 핵심 사실이 없어 문제를 만들지 않습니다.";
            case CORRECT_MISCONCEPTION -> "대화에서 헷갈린 지점이 없어 문제를 만들지 않습니다.";
        };
    }
}
