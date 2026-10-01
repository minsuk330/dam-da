package com.khack.review.collection.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks a connector call against the schema rules (spec §7.6). Only what the server cannot repair is an
 * error, because a rejected call makes the model regenerate the whole argument; everything else is a warning
 * for the app confirmation step.
 */
public final class SessionValidator {

    static final int MAX_REVIEW_UNITS = 7;
    static final int MAX_KEY_POINTS = 10;
    private static final Set<AiVerdict> CORRECTED = Set.of(AiVerdict.partial, AiVerdict.corrected);

    private SessionValidator() {
    }

    public static ValidationResult validate(SessionInput input) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<UserTurn> turns = input.userTurns() == null ? List.of() : input.userTurns();
        List<ReviewUnit> units = input.reviewUnits() == null ? List.of() : input.reviewUnits();

        if (turns.isEmpty()) {
            errors.add("userTurns: 비어 있습니다. 대화의 모든 사용자 메시지를 하나씩 넣으세요.");
        }
        Map<Integer, UserTurn> byIndex = new HashMap<>();
        for (int i = 0; i < turns.size(); i++) {
            UserTurn t = turns.get(i);
            if (t.text() == null || t.text().isBlank()) {
                errors.add("userTurns[%d].text: 비어 있습니다. 사용자 메시지 원문을 넣으세요.".formatted(i));
            }
            if (t.index() < 1) {
                errors.add("userTurns[%d].index=%d: index는 1부터 시작하는 대화 순서입니다.".formatted(i, t.index()));
            } else if (byIndex.putIfAbsent(t.index(), t) != null) {
                errors.add("userTurns[%d].index=%d: 중복된 index입니다. 사용자 메시지마다 다른 순번을 쓰세요.".formatted(i, t.index()));
            }
            if (CORRECTED.contains(t.effectiveVerdict()) && (t.correction() == null || t.correction().isBlank())) {
                warnings.add("userTurns[%d].correction: aiVerdict가 %s인데 교정 내용이 비어 있습니다.".formatted(i, t.effectiveVerdict()));
            }
        }

        if (units.isEmpty()) {
            errors.add("reviewUnits: 비어 있습니다. 대화에서 복습할 주제를 1~%d개 뽑으세요.".formatted(MAX_REVIEW_UNITS));
        } else if (units.size() > MAX_REVIEW_UNITS) {
            warnings.add("reviewUnits: %d개입니다. 권장 개수는 1~%d개입니다.".formatted(units.size(), MAX_REVIEW_UNITS));
        }
        Set<Integer> covered = new HashSet<>();
        Set<Integer> confused = new HashSet<>();
        for (int u = 0; u < units.size(); u++) {
            checkUnit(u, units.get(u), byIndex, errors, warnings);
            List<KeyPoint> points = units.get(u).keyPoints() == null ? List.of() : units.get(u).keyPoints();
            points.stream().filter(p -> p.turns() != null).forEach(p -> covered.addAll(p.turns()));
            units.get(u).confusions().forEach(c -> confused.add(c.turn()));
        }
        for (int i = 0; i < turns.size(); i++) {
            UserTurn t = turns.get(i);
            if (t.intent() != Intent.meta && t.index() >= 1 && !covered.contains(t.index())) {
                warnings.add("userTurns[%d]: %d번 학습 발화의 AI 답변이 어느 복습 단위에도 반영되지 않았습니다.".formatted(i, t.index()));
            }
            if (CORRECTED.contains(t.effectiveVerdict()) && !confused.contains(t.index())) {
                warnings.add("userTurns[%d]: %d번 발화의 aiVerdict가 %s인데 어느 confusionPoints에도 없습니다. 사용자가 믿었던 내용(userBelief)이 빠졌습니다."
                        .formatted(i, t.index(), t.effectiveVerdict()));
            }
        }
        return new ValidationResult(List.copyOf(errors), List.copyOf(warnings));
    }

    private static void checkUnit(int u, ReviewUnit unit, Map<Integer, UserTurn> byIndex,
            List<String> errors, List<String> warnings) {
        String at = "reviewUnits[%d]".formatted(u);
        if (unit.title() == null || unit.title().isBlank()) {
            errors.add(at + ".title: 비어 있습니다. 복습 주제 이름을 넣으세요.");
        }
        List<KeyPoint> points = unit.keyPoints() == null ? List.of() : unit.keyPoints();
        if (points.isEmpty()) {
            errors.add(at + ".keyPoints: 비어 있습니다. 이 주제에 대한 AI 답변의 핵심을 1개 이상 넣으세요.");
        } else if (points.size() > MAX_KEY_POINTS) {
            warnings.add(at + ".keyPoints: %d개입니다. 권장 개수는 1~%d개입니다.".formatted(points.size(), MAX_KEY_POINTS));
        }
        for (int p = 0; p < points.size(); p++) {
            KeyPoint point = points.get(p);
            String where = at + ".keyPoints[%d]".formatted(p);
            if (point.point() == null || point.point().isBlank()) {
                errors.add(where + ".point: 비어 있습니다. AI 답변의 핵심 내용을 넣으세요.");
            }
            List<Integer> turns = point.turns() == null ? List.of() : point.turns();
            if (turns.isEmpty()) {
                errors.add(where + ".turns: 비어 있습니다. 이 내용을 설명한 AI 답변 직전 사용자 발화 index를 넣으세요.");
            }
            for (int turn : turns) {
                UserTurn t = byIndex.get(turn);
                if (t == null) {
                    errors.add(where + ".turns: " + turn + "번 발화가 userTurns에 없습니다. userTurns의 index만 참조하세요.");
                } else if (t.intent() == Intent.meta) {
                    warnings.add(where + ".turns: " + turn + "번 발화는 meta 발화라 출처가 될 수 없습니다.");
                }
            }
        }
        List<ConfusionPoint> confusions = unit.confusions();
        for (int c = 0; c < confusions.size(); c++) {
            int turn = confusions.get(c).turn();
            String where = at + ".confusionPoints[%d].turn=%d".formatted(c, turn);
            UserTurn t = byIndex.get(turn);
            if (t == null) {
                errors.add(where + ": userTurns에 없는 발화입니다. userTurns의 index만 참조하세요.");
            } else if (!CORRECTED.contains(t.effectiveVerdict())) {
                warnings.add(where + ": 발화 %d의 aiVerdict가 %s입니다. 헷갈린 지점은 보통 partial 또는 corrected 발화입니다."
                        .formatted(turn, t.effectiveVerdict()));
            }
        }
    }
}
