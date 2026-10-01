package com.khack.review.question.domain;

import java.util.Arrays;
import java.util.List;

public final class Sources {

    private Sources() {
    }

    public static QuestionSource.Turn turn(int index) {
        return new QuestionSource.Turn(index, "발화 " + index, false);
    }

    public static QuestionSource.Turn meta(int index) {
        return new QuestionSource.Turn(index, "복습에 넣어줘", true);
    }

    public static QuestionSource.KeyPoint point(PointKind kind, int... turns) {
        return new QuestionSource.KeyPoint(kind + " 사실 " + Arrays.toString(turns), kind,
                Arrays.stream(turns).boxed().toList());
    }

    public static QuestionSource.Confusion confusion(int turn) {
        return new QuestionSource.Confusion(turn, "잘못 믿은 내용", "AI의 교정");
    }

    public static QuestionSource.Unit unit(List<QuestionSource.KeyPoint> keyPoints, QuestionSource.Confusion... confusions) {
        return new QuestionSource.Unit("주제", keyPoints, List.of(confusions));
    }

    public static QuestionSource source(List<QuestionSource.Turn> turns, QuestionSource.Unit... units) {
        return new QuestionSource("session-1", turns, List.of(units));
    }

    /** ETag 대화: 단위 0은 사실 3개 + 헷갈린 지점 1개, 단위 1은 사실·경고·실무 팁 1개씩. 6번은 meta 발화. */
    public static QuestionSource etag() {
        return source(
                List.of(turn(1), turn(2), turn(3), turn(4), turn(5), meta(6)),
                unit(List.of(point(PointKind.FACT, 1, 3), point(PointKind.FACT, 1, 3, 4), point(PointKind.FACT, 2)),
                        confusion(2)),
                unit(List.of(point(PointKind.FACT, 5), point(PointKind.WARNING, 5), point(PointKind.PRACTICE, 5))));
    }
}
