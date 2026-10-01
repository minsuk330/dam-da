package com.khack.review.practice.domain;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 학습 목표로 첫 학습 문제를 구성한다 (스펙 §7.8 표). 모든 문제는 기억 항목 하나를 대상으로 한다(§6.4.1).
 * 첫 학습에는 문제 유형 사다리(§6.4.6)를 적용하지 않는다. 상한을 넘으면 헷갈린 지점 문제를 먼저 남기고,
 * 문제에 들지 못한 항목은 신규 항목으로 남아 매일 학습에 편성된다(§6.4.4). 기억 항목 하나에는 문제 하나만 내며, 대표 핵심 사실을
 * 쓰는 목표는 그 항목이 이미 쓰였으면 같은 단위의 다른 핵심 사실로 옮긴다.
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
        List<Slot> slots = new ArrayList<>();
        for (LearningGoal goal : goals) {
            slots.addAll(slots(goal, units));
        }
        List<Slot> ordered = new ArrayList<>(slots);
        ordered.sort(Comparator.comparing((Slot slot) -> slot.targets().getFirst().kind() != MemoryItemKind.CONFUSION)
                .thenComparing(slot -> goals.indexOf(slot.goal())));

        // 기억 항목 하나에는 문제 하나만 낸다. 같은 항목의 두 번째 문제는 품질 검사의 중복 판정에서 거의 다 떨어져 생성만 되풀이한다.
        // 이미 쓴 항목이면 자리의 다음 후보로 옮기고, 후보가 없으면 그 자리는 버린다.
        Set<Long> used = new HashSet<>();
        Set<LearningGoal> crowdedOut = new HashSet<>();
        List<PlannedQuestion> planned = new ArrayList<>();
        for (Slot slot : ordered) {
            if (planned.size() >= maxQuestions) {
                break;
            }
            Optional<Item> target = slot.targets().stream().filter(i -> !used.contains(i.id())).findFirst();
            if (target.isEmpty()) {
                crowdedOut.add(slot.goal());
                continue;
            }
            used.add(target.get().id());
            Long related = slot.comparable().stream().map(Item::id).filter(id -> !id.equals(target.get().id()))
                    .findFirst().orElse(null);
            planned.add(new PlannedQuestion(slot.goal(), target.get().id(), related, slot.type()));
        }

        List<GoalSummary> summaries = goals.stream().map(goal -> {
            int candidateCount = (int) slots.stream().filter(slot -> slot.goal() == goal).count();
            int plannedCount = (int) planned.stream().filter(q -> q.goal() == goal).count();
            String reason = candidateCount == 0 ? noTargetReason(goal)
                    : plannedCount == 0 && crowdedOut.contains(goal) ? CROWDED_OUT : null;
            return new GoalSummary(goal, candidateCount, plannedCount, reason);
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
        return new Composition(List.copyOf(planned), summaries, unplanned, maxQuestions);
    }

    private static final String CROWDED_OUT = "다른 목표의 문제가 대상 항목을 모두 다뤄 문제를 만들지 않습니다.";

    /**
     * 문제 자리 하나. 대상 항목을 {@code targets} 순서로 고른다(첫 항목이 표의 대상, 나머지는 이미 쓰였을 때 옮겨 갈 같은 단위의 핵심 사실).
     * {@code comparable}은 개념 구분하기의 비교 대상 후보다.
     */
    private record Slot(LearningGoal goal, QuestionType type, List<Item> targets, List<Item> comparable) {

        static Slot fixed(LearningGoal goal, QuestionType type, Item item) {
            return new Slot(goal, type, List.of(item), List.of());
        }
    }

    private static List<Slot> slots(LearningGoal goal, List<Unit> units) {
        List<Slot> out = new ArrayList<>();
        for (Unit unit : units) {
            switch (goal) {
                case KEY_RECALL -> unit.items().stream().filter(i -> i.kind() == MemoryItemKind.FACT)
                        .forEach(i -> out.add(Slot.fixed(goal, QuestionType.SHORT_ANSWER, i)));
                case PRINCIPLE -> unit.representative()
                        .ifPresent(r -> out.add(new Slot(goal, QuestionType.ESSAY, keyPointsFrom(unit, List.of(r)), List.of())));
                case DISTINGUISH -> {
                    if (unit.keyPoints().size() >= 2) {
                        out.add(new Slot(goal, QuestionType.MULTIPLE_CHOICE,
                                keyPointsFrom(unit, List.of(unit.representative().orElseThrow())), unit.keyPoints()));
                    }
                }
                case CONDITION -> unit.items().stream().filter(i -> i.kind() == MemoryItemKind.WARNING)
                        .forEach(i -> out.add(Slot.fixed(goal, QuestionType.CASE_JUDGMENT, i)));
                case APPLY_CASE -> {
                    List<Item> preferred = new ArrayList<>(unit.items().stream().filter(i -> i.kind() == MemoryItemKind.PRACTICE).toList());
                    unit.representative().ifPresent(preferred::add);
                    if (!preferred.isEmpty()) {
                        out.add(new Slot(goal, QuestionType.CASE_APPLICATION, keyPointsFrom(unit, preferred), List.of()));
                    }
                }
                case CORRECT_MISCONCEPTION -> unit.items().stream().filter(i -> i.kind() == MemoryItemKind.CONFUSION)
                        .forEach(i -> out.add(Slot.fixed(goal, QuestionType.ERROR_FINDING, i)));
                case EXPLAIN_OWN_WORDS -> unit.representative()
                        .ifPresent(r -> out.add(new Slot(goal, QuestionType.ESSAY, keyPointsFrom(unit, List.of(r)), List.of())));
            }
        }
        return out;
    }

    /** 표의 대상({@code preferred})을 먼저, 이어서 같은 단위의 나머지 핵심 사실을 저장된 순서로. */
    private static List<Item> keyPointsFrom(Unit unit, List<Item> preferred) {
        Set<Item> ordered = new LinkedHashSet<>(preferred);
        ordered.addAll(unit.keyPoints());
        return List.copyOf(ordered);
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
