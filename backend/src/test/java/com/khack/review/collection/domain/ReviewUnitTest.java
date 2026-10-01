package com.khack.review.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewUnitTest {

    @Test
    void evidenceTurnsAreTheSortedUnionOfKeyPointAndConfusionTurns() {
        ReviewUnit unit = new ReviewUnit("t",
                List.of(new KeyPoint("a", List.of(4, 1), null), new KeyPoint("b", List.of(1, 7), FactKind.practice)),
                List.of(new ConfusionPoint(3, "x"), new ConfusionPoint(4, "y")));
        assertThat(unit.evidenceTurns()).containsExactly(1, 3, 4, 7);
    }

    @Test
    void defaultsApplyWhenOptionalFieldsAreOmitted() {
        assertThat(new KeyPoint("a", List.of(1), null).effectiveKind()).isEqualTo(FactKind.fact);
        assertThat(new UserTurn(1, "q", null, Intent.info_request, null, null).effectiveVerdict()).isEqualTo(AiVerdict.not_applicable);
        assertThat(new ReviewUnit("t", List.of(new KeyPoint("a", List.of(2), null)), null).evidenceTurns()).containsExactly(2);
    }
}
