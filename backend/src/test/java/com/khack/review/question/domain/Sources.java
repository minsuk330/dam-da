package com.khack.review.question.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public final class Sources {

    private Sources() {
    }

    public static QuestionSource.Turn turn(int index) {
        return new QuestionSource.Turn(index, "발화 " + index);
    }

    public static QuestionSource.Item point(ItemKind kind, int... turns) {
        return new QuestionSource.Item(null, kind, kind + " 사실 " + Arrays.toString(turns),
                Arrays.stream(turns).boxed().toList(), null);
    }

    public static QuestionSource.Item confusion(int turn) {
        return new QuestionSource.Item(null, ItemKind.CONFUSION, "잘못 믿은 내용", List.of(turn), "AI의 교정");
    }

    /** 기억 항목 순서는 서버와 같다: 핵심 사실 다음에 헷갈린 지점. */
    public static QuestionSource.Unit unit(List<QuestionSource.Item> keyPoints, QuestionSource.Item... confusions) {
        return new QuestionSource.Unit("주제", Stream.concat(keyPoints.stream(), Arrays.stream(confusions)).toList());
    }

    public static QuestionSource source(List<QuestionSource.Turn> turns, QuestionSource.Unit... units) {
        return new QuestionSource(null, turns, List.of(units));
    }

    /** ETag 대화: 단위 0은 사실 3개 + 헷갈린 지점 1개(항목 3), 단위 1은 사실·경고·실무 팁 1개씩. */
    public static QuestionSource etag() {
        return source(
                List.of(turn(1), turn(2), turn(3), turn(4), turn(5)),
                unit(List.of(point(ItemKind.FACT, 1, 3), point(ItemKind.FACT, 1, 3, 4), point(ItemKind.FACT, 2)),
                        confusion(2)),
                unit(List.of(point(ItemKind.FACT, 5), point(ItemKind.WARNING, 5), point(ItemKind.PRACTICE, 5))));
    }
}
