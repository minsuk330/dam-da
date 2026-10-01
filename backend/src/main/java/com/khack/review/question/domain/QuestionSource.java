package com.khack.review.question.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 문제 생성의 입력. 사용자가 앱에서 확인을 마친 학습 세션의 복습 단위와 기억 항목이다(스펙 규칙 12).
 * 다른 컨텍스트의 엔티티를 직접 참조하지 않도록 필요한 값만 옮긴 것이다.
 * {@code sessionId}와 {@code memoryItemId}는 저장하지 않고 결과만 보는 개발 도구에서는 null이다.
 */
public record QuestionSource(@Nullable Long sessionId, List<Turn> turns, List<Unit> units) {

    /** 근거 발화의 사용자 원문. */
    public record Turn(int index, String text) {
    }

    public record Unit(String title, List<Item> items) {
    }

    /**
     * @param content     핵심 사실이면 그 내용, 헷갈린 지점이면 사용자가 믿었던 내용
     * @param sourceTurns 출처 발화 index. `meta` 발화는 들어 있지 않다(스펙 규칙 15)
     * @param correction  헷갈린 지점에만 있다. 출처 발화에 대한 대화 속 AI의 교정
     */
    public record Item(@Nullable Long memoryItemId, ItemKind kind, String content, List<Integer> sourceTurns,
            @Nullable String correction) {
    }

    public Item item(QuestionTarget target) {
        return units.get(target.unitIndex()).items().get(target.itemIndex());
    }
}
