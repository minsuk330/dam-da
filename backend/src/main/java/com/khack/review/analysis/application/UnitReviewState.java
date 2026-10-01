package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.analysis.domain.ReviewUnit;
import com.khack.review.collection.domain.UserTurn;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 복습 단위 검수에서 Jev에 보내는 상태. {@link UnitReviewQuestions}가 필드 이름을 가리킨다.
 * {@code evidence}는 복습 단위의 근거 발화만 담는다.
 */
public record UnitReviewState(@Nullable String topic, String unitTitle, List<Item> items, List<EvidenceTurn> evidence) {

    public record Item(String kind, String content, List<Integer> sourceTurns) {
    }

    public record EvidenceTurn(int index, String text, String intent, @Nullable String aiVerdict,
            @Nullable String correction) {
    }

    public static UnitReviewState of(@Nullable String topic, ReviewUnit unit, List<UserTurn> turns) {
        Set<Integer> evidenceTurns = Set.copyOf(unit.evidenceTurns());
        return new UnitReviewState(topic, unit.getTitle(),
                unit.getItems().stream().map(UnitReviewState::item).toList(),
                turns.stream().filter(turn -> evidenceTurns.contains(turn.index())).map(UnitReviewState::evidence).toList());
    }

    private static Item item(MemoryItem item) {
        return new Item(item.getKind().name().toLowerCase(), item.getContent(), item.getSourceTurns());
    }

    private static EvidenceTurn evidence(UserTurn turn) {
        return new EvidenceTurn(turn.index(), turn.text(), turn.intent().name(),
                turn.aiVerdict() == null ? null : turn.aiVerdict().name(), turn.correction());
    }
}
