package com.khack.review.memory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.UserTurn;
import io.github.openspacedrepetition.Rating;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 스펙 §6.4.2 초기 평가 표. 빈 칸은 aiVerdict 생략(=not_applicable), 결과 NONE은 평가 없음. */
class InitialRatingTest {

    static UserTurn turn(String intent, String verdict) {
        return new UserTurn(1, "발화", null, Intent.valueOf(intent), verdict == null ? null : AiVerdict.valueOf(verdict), null);
    }

    static Optional<Rating> expected(String rating) {
        return rating.equals("NONE") ? Optional.empty() : Optional.of(Rating.valueOf(rating));
    }

    @ParameterizedTest(name = "{0} + {1} → {2}")
    @CsvSource({
            "understanding_check, corrected,      AGAIN",
            "understanding_check, partial,        AGAIN",
            "restatement,         corrected,      AGAIN",
            "challenge,           partial,        AGAIN",
            "info_request,        corrected,      AGAIN",
            "understanding_check, confirmed,      GOOD",
            "restatement,         confirmed,      GOOD",
            "info_request,        confirmed,      NONE",
            "challenge,           confirmed,      NONE",
            "rephrase_request,    confirmed,      NONE",
            "understanding_check, not_applicable, NONE",
            "understanding_check,               , NONE",
            "info_request,                      , NONE",
    })
    void singleSourceTurn(String intent, String verdict, String rating) {
        assertThat(InitialRating.of(List.of(turn(intent, verdict)))).isEqualTo(expected(rating));
    }

    /** 각 출처는 intent:verdict, 여러 개는 공백으로 나눈다. 신호 없는 발화는 비교에서 빠진다. */
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "info_request: understanding_check:confirmed,                  GOOD",
            "understanding_check:confirmed understanding_check:corrected,   AGAIN",
            "info_request: understanding_check:partial restatement:confirmed, AGAIN",
            "info_request: rephrase_request:,                               NONE",
    })
    void lowestSignalWinsAcrossSourceTurns(String sources, String rating) {
        List<UserTurn> turns = Arrays.stream(sources.trim().split("\\s+")).map(source -> {
            String[] parts = source.split(":", -1);
            return turn(parts[0], parts[1].isEmpty() ? null : parts[1]);
        }).toList();

        assertThat(InitialRating.of(turns)).isEqualTo(expected(rating));
    }

    @ParameterizedTest(name = "출처 없음 → NONE")
    @CsvSource("NONE")
    void noSourceTurns(String rating) {
        assertThat(InitialRating.of(List.of())).isEqualTo(expected(rating));
    }
}
