package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.ReviewUnitVerdict;

/** 복습 단위 1개의 검수 결과와 그 이유. */
public record UnitReviewOutcome(ReviewUnitVerdict verdict, String reason) {
}
