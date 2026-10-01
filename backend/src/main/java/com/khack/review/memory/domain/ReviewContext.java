package com.khack.review.memory.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 복습 기록에 함께 남길 풀이 정보 (스펙 §6.4.5 기록). 등급 변환에는 쓰지 않는다.
 *
 * @param reviewedAt              답변 제출 시각. FSRS 갱신 시각이다
 * @param predictedRetrievability 답변 직전(제시 시점)의 예측 R
 * @param elapsedSincePriorMs     지연된 재확인일 때 직전 도움(없으면 직전 답) 이후 경과 시간
 * @param failures                판정 이유(omission, contradiction, misread). 객관식이면 비어 있다
 */
public record ReviewContext(
        Long userId,
        Long memoryItemId,
        Long questionId,
        Long attemptId,
        Instant reviewedAt,
        @Nullable Double predictedRetrievability,
        boolean priorAidExposed,
        @Nullable Long elapsedSincePriorMs,
        boolean sameDayRecheck,
        @Nullable List<String> failures,
        @Nullable Long firstInputMs) {
}
