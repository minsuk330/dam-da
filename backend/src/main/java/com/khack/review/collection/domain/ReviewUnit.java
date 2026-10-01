package com.khack.review.collection.domain;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public record ReviewUnit(String title, List<KeyPoint> keyPoints, @Nullable List<ConfusionPoint> confusionPoints) {

    public List<ConfusionPoint> confusions() {
        return confusionPoints == null ? List.of() : confusionPoints;
    }

    /** Computed rather than sent: the union of key point turns and confusion turns, sorted. */
    public List<Integer> evidenceTurns() {
        Stream<Integer> fromPoints = keyPoints == null ? Stream.empty()
                : keyPoints.stream().filter(p -> p.turns() != null).flatMap(p -> p.turns().stream());
        Stream<Integer> fromConfusions = confusions().stream().map(ConfusionPoint::turn);
        return List.copyOf(new TreeSet<>(Stream.concat(fromPoints, fromConfusions).toList()));
    }
}
