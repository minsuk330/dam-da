package com.khack.review.question.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 학습 목표로 첫 학습의 문제 대상·유형·개수를 정한다(스펙 §7.8). LLM에게 묻지 않고 코드로 계산한다.
 */
public final class QuestionPlanner {

    /** 첫 학습 전체 문제 수 상한. 초기값이며 조정한다. */
    public static final int FIRST_STUDY_LIMIT = 12;

    private QuestionPlanner() {
    }

    public static QuestionPlan plan(QuestionSource source, Collection<LearningGoal> goals) {
        Set<LearningGoal> selected = new TreeSet<>(goals);
        if (selected.isEmpty() || selected.size() > LearningGoal.MAX_PER_SESSION) {
            throw new IllegalArgumentException(
                    "학습 목표는 1~" + LearningGoal.MAX_PER_SESSION + "개를 골라야 합니다: " + goals);
        }
        Set<Integer> metaTurns = source.turns().stream()
                .filter(QuestionSource.Turn::meta)
                .map(QuestionSource.Turn::index)
                .collect(Collectors.toSet());

        List<QuestionTarget> all = new ArrayList<>();
        List<QuestionPlan.Skipped> skipped = new ArrayList<>();
        for (int u = 0; u < source.units().size(); u++) {
            UsableUnit unit = UsableUnit.of(u, source.units().get(u), metaTurns, skipped);
            for (LearningGoal goal : selected) {
                all.addAll(unit.targets(goal));
            }
        }

        Map<LearningGoal, String> emptyGoals = new EnumMap<>(LearningGoal.class);
        for (LearningGoal goal : selected) {
            if (all.stream().noneMatch(t -> t.goal() == goal)) {
                emptyGoals.put(goal, emptyReason(goal));
            }
        }

        // 헷갈린 지점 항목을 먼저 넣는다. 나머지는 복습 단위 순서, 같은 단위 안에서는 목표 순서를 유지한다.
        List<QuestionTarget> ordered = all.stream()
                .sorted(Comparator.comparing((QuestionTarget t) -> t.itemKind() != ItemKind.CONFUSION_POINT))
                .toList();
        int limit = Math.min(FIRST_STUDY_LIMIT, ordered.size());
        return new QuestionPlan(
                List.copyOf(ordered.subList(0, limit)),
                List.copyOf(ordered.subList(limit, ordered.size())),
                emptyGoals,
                List.copyOf(skipped));
    }

    private static String emptyReason(LearningGoal goal) {
        return switch (goal) {
            case REMEMBER_CORE -> "개념 설명(fact) 핵심 사실이 없습니다.";
            case JUDGE_CONDITIONS -> "경고(warning) 핵심 사실이 없습니다.";
            case CORRECT_MISCONCEPTION -> "대화에 헷갈린 지점이 없습니다.";
            case DISTINGUISH_CONCEPTS -> "서로 비교할 핵심 사실이 2개 이상인 복습 단위가 없습니다.";
            case UNDERSTAND_PRINCIPLE, APPLY_TO_CASE, EXPLAIN_IN_OWN_WORDS -> "근거 발화가 있는 핵심 사실이 없습니다.";
        };
    }

    /** 근거 발화가 있어 문제로 만들 수 있는 항목만 추린 복습 단위. */
    private record UsableUnit(int unitIndex, List<Item> keyPoints, List<Item> confusions) {

        /** {@code kind}는 핵심 사실에만 있다. */
        private record Item(int index, @Nullable PointKind kind, List<Integer> evidenceTurns) {
        }

        static UsableUnit of(int unitIndex, QuestionSource.Unit unit, Set<Integer> metaTurns,
                List<QuestionPlan.Skipped> skipped) {
            List<Item> keyPoints = new ArrayList<>();
            for (int i = 0; i < unit.keyPoints().size(); i++) {
                QuestionSource.KeyPoint point = unit.keyPoints().get(i);
                List<Integer> evidence = point.turns().stream().filter(t -> !metaTurns.contains(t)).sorted().toList();
                if (evidence.isEmpty()) {
                    skipped.add(new QuestionPlan.Skipped(unitIndex, ItemKind.KEY_POINT, i, "근거 발화가 없습니다."));
                } else {
                    keyPoints.add(new Item(i, point.kind(), evidence));
                }
            }
            List<Item> confusions = new ArrayList<>();
            for (int i = 0; i < unit.confusions().size(); i++) {
                QuestionSource.Confusion confusion = unit.confusions().get(i);
                if (metaTurns.contains(confusion.turn())) {
                    skipped.add(new QuestionPlan.Skipped(unitIndex, ItemKind.CONFUSION_POINT, i, "근거 발화가 없습니다."));
                } else if (confusion.correction() == null || confusion.correction().isBlank()) {
                    skipped.add(new QuestionPlan.Skipped(unitIndex, ItemKind.CONFUSION_POINT, i,
                            "대화 속 AI 교정이 없어 정답의 근거가 없습니다."));
                } else {
                    confusions.add(new Item(i, null, List.of(confusion.turn())));
                }
            }
            return new UsableUnit(unitIndex, keyPoints, confusions);
        }

        List<QuestionTarget> targets(LearningGoal goal) {
            return switch (goal) {
                case REMEMBER_CORE -> each(goal, QuestionType.SHORT_ANSWER, PointKind.FACT);
                case UNDERSTAND_PRINCIPLE -> one(goal, QuestionType.DESCRIPTIVE, representative());
                case DISTINGUISH_CONCEPTS -> keyPoints.size() < 2 ? List.of()
                        : one(goal, QuestionType.MULTIPLE_CHOICE, representative());
                case JUDGE_CONDITIONS -> each(goal, QuestionType.CASE_JUDGMENT, PointKind.WARNING);
                case APPLY_TO_CASE -> one(goal, QuestionType.CASE_APPLICATION,
                        keyPoints.stream().filter(p -> p.kind() == PointKind.PRACTICE).findFirst().or(this::representative));
                case CORRECT_MISCONCEPTION -> confusions.stream()
                        .map(c -> target(goal, QuestionType.ERROR_FINDING, ItemKind.CONFUSION_POINT, c))
                        .toList();
                case EXPLAIN_IN_OWN_WORDS -> one(goal, QuestionType.DESCRIPTIVE, representative());
            };
        }

        /** 대표 핵심 사실: 가장 많은 발화에서 다뤄진 개념 설명. 개념 설명이 없으면 전체에서 고른다. */
        private Optional<Item> representative() {
            List<Item> facts = keyPoints.stream().filter(p -> p.kind() == PointKind.FACT).toList();
            // 근거 발화 수가 같으면 먼저 나온 것을 고른다(Stream.max는 마지막 것을 돌려주므로 reduce를 쓴다).
            return (facts.isEmpty() ? keyPoints : facts).stream()
                    .reduce((a, b) -> b.evidenceTurns().size() > a.evidenceTurns().size() ? b : a);
        }

        private List<QuestionTarget> each(LearningGoal goal, QuestionType type, PointKind kind) {
            return keyPoints.stream()
                    .filter(p -> p.kind() == kind)
                    .map(p -> target(goal, type, ItemKind.KEY_POINT, p))
                    .toList();
        }

        private List<QuestionTarget> one(LearningGoal goal, QuestionType type, Optional<Item> item) {
            return item.map(p -> List.of(target(goal, type, ItemKind.KEY_POINT, p))).orElse(List.of());
        }

        private QuestionTarget target(LearningGoal goal, QuestionType type, ItemKind itemKind, Item item) {
            return new QuestionTarget(goal, type, unitIndex, itemKind, item.index(), item.evidenceTurns());
        }
    }
}
