package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.analysis.domain.MemoryItemStatus;
import com.khack.review.analysis.domain.ReviewUnitVerdict;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 확인 화면의 학습 세션 (도메인 스토리 S1-8). 발화, 복습 단위와 Jev 검수 결과, 기억 항목, 재검증 경고, 원문 여부를 담는다.
 * {@code fidelity}가 `model_transcribed`이면 발화는 모델이 옮겨 적은 것이라 사용자가 확인해야 한다(규칙 13).
 */
public record LearningSessionDetail(
        Long id,
        String conversationId,
        LearningSessionStatus status,
        @Nullable String topicHint,
        String inputPath,
        String fidelity,
        Instant createdAt,
        @Nullable Instant confirmedAt,
        List<Turn> turns,
        List<Unit> units,
        List<String> warnings) {

    public record Turn(int index, String text, @Nullable String quotedText, Intent intent, @Nullable AiVerdict aiVerdict,
            @Nullable String correction) {
    }

    public record Unit(Long id, String title, boolean excluded, ReviewUnitVerdict verdict, @Nullable String verdictReason,
            List<Integer> evidenceTurns, List<Item> items) {
    }

    public record Item(Long id, MemoryItemKind kind, String content, List<Integer> sourceTurns, MemoryItemStatus status) {
    }
}
