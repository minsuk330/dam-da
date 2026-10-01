package com.khack.review.collection.domain;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static com.khack.review.collection.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionValidatorTest {

    private static final List<UserTurn> TURNS = List.of(
            turn(1, "락은 스캔 후에 거는 거지?", Intent.understanding_check, AiVerdict.corrected, "읽는 순간 잠근다"),
            turn(2, "trx_id가 뭔데", Intent.info_request, null, null),
            turn(3, "복습에 넣어줘", Intent.meta, null, null));

    private static ValidationResult validate(List<UserTurn> turns, List<ReviewUnit> units) {
        return SessionValidator.validate(new SessionInput(turns, units, null));
    }

    @Test
    void acceptsWellFormedSessionCoveringEveryLearningTurn() {
        ReviewUnit unit = new ReviewUnit("잠금", List.of(point("레코드마다 즉시 잠근다", 1), point("trx_id는 일련번호", 2)),
                List.of(new ConfusionPoint(1, "스캔 후 잠근다")));
        ValidationResult r = validate(TURNS, List.of(unit));
        assertThat(r.errors()).isEmpty();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void rejectsMissingTurnsAndUnits() {
        assertThat(validate(List.of(), List.of(unit("t", 1))).errors()).anyMatch(e -> e.startsWith("userTurns"));
        assertThat(validate(TURNS, List.of()).errors()).anyMatch(e -> e.startsWith("reviewUnits"));
    }

    @Test
    void rejectsBlankTextAndBadOrDuplicateIndexes() {
        List<String> errors = validate(List.of(turn(1, " "), turn(1, "dup"), turn(0, "zero")), List.of(unit("t", 1))).errors();
        assertThat(errors).anyMatch(e -> e.startsWith("userTurns[0].text"));
        assertThat(errors).anyMatch(e -> e.startsWith("userTurns[1].index=1") && e.contains("중복"));
        assertThat(errors).anyMatch(e -> e.startsWith("userTurns[2].index=0"));
    }

    @Test
    void rejectsUnitWithoutTitleOrKeyPointsAndBlankOrUnanchoredPoints() {
        List<String> errors = validate(TURNS, List.of(
                new ReviewUnit(" ", List.of(), null),
                new ReviewUnit("t", List.of(new KeyPoint(" ", List.of(1), null), new KeyPoint("p", List.of(), null)), null))).errors();
        assertThat(errors).anyMatch(e -> e.startsWith("reviewUnits[0].title"));
        assertThat(errors).anyMatch(e -> e.startsWith("reviewUnits[0].keyPoints"));
        assertThat(errors).anyMatch(e -> e.startsWith("reviewUnits[1].keyPoints[0].point"));
        assertThat(errors).anyMatch(e -> e.startsWith("reviewUnits[1].keyPoints[1].turns"));
    }

    @Test
    void rejectsReferencesToUnknownTurnsWithFixHint() {
        ReviewUnit unit = new ReviewUnit("t", List.of(point("p", 9)), List.of(new ConfusionPoint(8, "b")));
        List<String> errors = validate(TURNS, List.of(unit)).errors();
        assertThat(errors).anyMatch(e -> e.startsWith("reviewUnits[0].keyPoints[0].turns") && e.contains("9") && e.contains("userTurns"));
        assertThat(errors).anyMatch(e -> e.startsWith("reviewUnits[0].confusionPoints[0].turn=8"));
    }

    @Test
    void warnsInsteadOfRejectingSemanticOddities() {
        List<UserTurn> turns = List.of(
                turn(1, "스캔 후 잠그지?", Intent.understanding_check, AiVerdict.partial, null),
                turn(2, "trx_id가 뭔데", Intent.info_request, null, null),
                turn(3, "복습에 넣어줘", Intent.meta, null, null));
        List<ReviewUnit> units = new ArrayList<>();
        List<KeyPoint> points = new ArrayList<>(Collections.nCopies(11, point("p", 1)));
        points.add(point("meta", 3));
        units.add(new ReviewUnit("t", points, List.of(new ConfusionPoint(2, "b"))));
        for (int i = 0; i < 7; i++) {
            units.add(unit("u" + i, 1));
        }
        ValidationResult r = validate(turns, units);
        assertThat(r.errors()).isEmpty();
        assertThat(r.warnings()).anyMatch(w -> w.startsWith("reviewUnits:") && w.contains("8"));
        assertThat(r.warnings()).anyMatch(w -> w.startsWith("reviewUnits[0].keyPoints:") && w.contains("12"));
        assertThat(r.warnings()).anyMatch(w -> w.startsWith("reviewUnits[0].keyPoints[11].turns") && w.contains("meta"));
        assertThat(r.warnings()).anyMatch(w -> w.startsWith("reviewUnits[0].confusionPoints[0].turn=2") && w.contains("partial"));
        assertThat(r.warnings()).anyMatch(w -> w.startsWith("userTurns[0].correction"));
        assertThat(r.warnings()).anyMatch(w -> w.startsWith("userTurns[1]") && w.contains("반영"));
        assertThat(r.warnings()).noneMatch(w -> w.startsWith("userTurns[2]"));
    }

    @Test
    void warnsWhenCorrectedTurnHasNoConfusionPoint() {
        List<UserTurn> turns = List.of(
                turn(1, "전부 비교해서 만드는 거지?", Intent.understanding_check, AiVerdict.corrected, "버전 식별자다"),
                turn(2, "정리하면 304지?", Intent.restatement, AiVerdict.partial, "대부분 맞다"),
                turn(3, "맞지?", Intent.understanding_check, AiVerdict.confirmed, null));
        ReviewUnit unit = new ReviewUnit("ETag", List.of(point("p", 1, 2, 3)), List.of(new ConfusionPoint(2, "항상 304")));
        List<String> warnings = validate(turns, List.of(unit)).warnings();
        assertThat(warnings).anyMatch(w -> w.startsWith("userTurns[0]") && w.contains("confusionPoints"));
        assertThat(warnings).noneMatch(w -> w.startsWith("userTurns[1]") && w.contains("confusionPoints"));
        assertThat(warnings).noneMatch(w -> w.startsWith("userTurns[2]"));
    }
}
