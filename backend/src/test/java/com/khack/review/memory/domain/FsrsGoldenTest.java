package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 옵티마이저(py-fsrs)와 스케줄러(java-fsrs)가 같은 FSRS 계산을 하는지 지키는 테스트 (스펙 §6.4.9).
 * 같은 기대값 파일을 {@code optimizer/tests/test_compat.py}도 확인한다. java-fsrs를 올리면 이 파일을 다시 만든다.
 */
class FsrsGoldenTest {

    private static final Path GOLDEN = Path.of("../optimizer/tests/fixtures/java_fsrs_golden.json");

    @Test
    void schedulerMatchesTheGoldenReviewsSharedWithTheOptimizer() {
        JsonNode cases = JsonMapper.builder().build().readTree(GOLDEN.toFile()).get("cases");

        assertThat(cases).isNotEmpty();
        assertThat(toArray(cases.get(0).get("parameters"))).containsExactly(FsrsSchedulers.defaultParameters());
        for (JsonNode golden : cases) {
            Scheduler scheduler = FsrsSchedulers.create(toArray(golden.get("parameters")), 0.9);
            Card card = null;
            for (JsonNode review : golden.get("reviews")) {
                Instant at = Instant.parse(review.get("datetime").asString());
                if (card == null) {
                    card = Card.builder().cardId(1).due(at).build();
                }
                if (!review.get("retrievability_before").isNull()) {
                    assertThat(scheduler.getCardRetrievability(card, at))
                            .isCloseTo(review.get("retrievability_before").asDouble(), within(1e-9));
                }
                card = scheduler.reviewCard(card, Rating.values()[review.get("rating").asInt() - 1], at).card();
                assertThat(card.getStability()).isCloseTo(review.get("stability").asDouble(), within(1e-9));
                assertThat(card.getDifficulty()).isCloseTo(review.get("difficulty").asDouble(), within(1e-9));
                assertThat(card.getDue()).isEqualTo(Instant.parse(review.get("due").asString()));
            }
        }
    }

    private static double[] toArray(JsonNode values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i).asDouble();
        }
        return result;
    }
}
