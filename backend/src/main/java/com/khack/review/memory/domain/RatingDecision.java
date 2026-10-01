package com.khack.review.memory.domain;

import io.github.openspacedrepetition.Rating;

/**
 * 등급 변환 결과. {@code row}는 적용한 변환표 행 번호(1~7)이고 {@code policyVersion}과 함께 풀이 기록에 남긴다(스펙 §6.4.5 기록).
 */
public sealed interface RatingDecision {

    int policyVersion();

    /** FSRS에 반영할 등급. 실패에는 Hard를 쓰지 않는다. */
    record Rated(Rating rating, int row, int policyVersion) implements RatingDecision {
    }

    /** 보류. 기억 상태를 바꾸지 않고 다음 매일 학습 큐 후보로 남긴다. */
    record Held(HoldReason reason, int row, int policyVersion) implements RatingDecision {
    }

    /** 평가 대상 시도가 아니다(힌트 후 재시도). 피드백 경로에만 쓴다. */
    record NotEvaluated(int policyVersion) implements RatingDecision {
    }
}
