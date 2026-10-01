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

        List<QuestionTarget> all = new ArrayList<>();
        List<QuestionPlan.Skipped> skipped = new ArrayList<>();
        for (int u = 0; u < source.units().size(); u++) {
            UsableUnit unit = UsableUnit.of(u, source.units().get(u), skipped);
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
                .sorted(Comparator.comparing((QuestionTarget t) -> t.itemKind() != ItemKind.CONFUSION))
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

    /** 근거가 있어 문제로 만들 수 있는 항목만 추린 복습 단위. */
    private record UsableUnit(int unitIndex, List<Usable> keyPoints, List<Usable> confusions) {

        private record Usable(int index, ItemKind kind, List<Integer> evidenceTurns) {
        }

        static UsableUnit of(int unitIndex, QuestionSource.Unit unit, List<QuestionPlan.Skipped> skipped) {
            List<Usable> keyPoints = new ArrayList<>();
            List<Usable> confusions = new ArrayList<>();
            for (int i = 0; i < unit.items().size(); i++) {
                QuestionSource.Item item = unit.items().get(i);
                Usable usable = new Usable(i, item.kind(), item.sourceTurns().stream().distinct().sorted().toList());
                if (usable.evidenceTurns().isEmpty()) {
                    skipped.add(new QuestionPlan.Skipped(unitIndex, i, "근거 발화가 없습니다."));
                } else if (item.kind() != ItemKind.CONFUSION) {
                    keyPoints.add(usable);
                } else if (item.correction() == null || item.correction().isBlank()) {
                    skipped.add(new QuestionPlan.Skipped(unitIndex, i, "대화 속 AI 교정이 없어 정답의 근거가 없습니다."));
                } else {
                    confusions.add(usable);
                }
            }
            return new UsableUnit(unitIndex, keyPoints, confusions);
        }

        List<QuestionTarget> targets(LearningGoal goal) {
            return switch (goal) {
                case REMEMBER_CORE -> each(goal, QuestionType.SHORT_ANSWER, ItemKind.FACT);
                case UNDERSTAND_PRINCIPLE -> one(goal, QuestionType.DESCRIPTIVE, representative());
                case DISTINGUISH_CONCEPTS -> keyPoints.size() < 2 ? List.of()
                        : one(goal, QuestionType.MULTIPLE_CHOICE, representative());
                case JUDGE_CONDITIONS -> each(goal, QuestionType.CASE_JUDGMENT, ItemKind.WARNING);
                case APPLY_TO_CASE -> one(goal, QuestionType.CASE_APPLICATION,
                        keyPoints.stream().filter(p -> p.kind() == ItemKind.PRACTICE).findFirst().or(this::representative));
                case CORRECT_MISCONCEPTION -> confusions.stream().map(c -> target(goal, QuestionType.ERROR_FINDING, c)).toList();
                case EXPLAIN_IN_OWN_WORDS -> one(goal, QuestionType.DESCRIPTIVE, representative());
            };
        }

        /** 대표 핵심 사실: 가장 많은 발화에서 다뤄진 개념 설명. 개념 설명이 없으면 전체에서 고른다. */
        private Optional<Usable> representative() {
            List<Usable> facts = keyPoints.stream().filter(p -> p.kind() == ItemKind.FACT).toList();
            // 근거 발화 수가 같으면 먼저 나온 것을 고른다(Stream.max는 마지막 것을 돌려주므로 reduce를 쓴다).
            return (facts.isEmpty() ? keyPoints : facts).stream()
                    .reduce((a, b) -> b.evidenceTurns().size() > a.evidenceTurns().size() ? b : a);
        }

        private List<QuestionTarget> each(LearningGoal goal, QuestionType type, ItemKind kind) {
            return keyPoints.stream().filter(p -> p.kind() == kind).map(p -> target(goal, type, p)).toList();
        }

        private List<QuestionTarget> one(LearningGoal goal, QuestionType type, Optional<Usable> item) {
            return item.map(p -> List.of(target(goal, type, p))).orElse(List.of());
        }

        private QuestionTarget target(LearningGoal goal, QuestionType type, Usable item) {
            return new QuestionTarget(goal, type, unitIndex, item.index(), item.kind(), item.evidenceTurns());
        }
    }
}
