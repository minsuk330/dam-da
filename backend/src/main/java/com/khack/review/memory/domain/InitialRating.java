package com.khack.review.memory.domain;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.UserTurn;
import io.github.openspacedrepetition.Rating;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * 대화를 0회차 복습으로 반영하는 초기 평가 (스펙 §6.4.2). 기억 항목의 출처 발화 신호로 정한다.
 *
 * <table>
 *   <tr><th>출처 발화 신호</th><th>초기 평가</th></tr>
 *   <tr><td>{@code aiVerdict} = corrected·partial</td><td>Again</td></tr>
 *   <tr><td>{@code intent} = understanding_check·restatement 이고 {@code aiVerdict} = confirmed</td><td>Good</td></tr>
 *   <tr><td>그 외 (정보 요청 등)</td><td>신호 없음</td></tr>
 * </table>
 *
 * 출처 발화가 여러 개면 신호가 있는 발화 중 가장 낮은 평가를 쓴다. 신호 없는 발화는 비교에서 빠진다.
 * 모든 출처에 신호가 없으면 평가하지 않고, 앱의 첫 풀이가 첫 평가가 된다. Hard·Easy는 쓰지 않는다.
 */
public final class InitialRating {

    private static final Set<AiVerdict> CORRECTED = Set.of(AiVerdict.corrected, AiVerdict.partial);
    private static final Set<Intent> SELF_CHECK = Set.of(Intent.understanding_check, Intent.restatement);

    private InitialRating() {
    }

    public static Optional<Rating> of(Collection<UserTurn> sourceTurns) {
        Optional<Rating> lowest = Optional.empty();
        for (UserTurn turn : sourceTurns) {
            Optional<Rating> rating = of(turn);
            if (rating.isPresent() && (lowest.isEmpty() || rating.get().compareTo(lowest.get()) < 0)) {
                lowest = rating;
            }
        }
        return lowest;
    }

    static Optional<Rating> of(UserTurn turn) {
        if (CORRECTED.contains(turn.effectiveVerdict())) {
            return Optional.of(Rating.AGAIN);
        }
        if (SELF_CHECK.contains(turn.intent()) && turn.effectiveVerdict() == AiVerdict.confirmed) {
            return Optional.of(Rating.GOOD);
        }
        return Optional.empty();
    }
}
