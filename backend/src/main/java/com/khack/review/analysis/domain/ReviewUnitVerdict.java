package com.khack.review.analysis.domain;

/**
 * Jev 복습 단위 검수 결과 (스펙 §6.2). 어느 값이든 복습 단위를 자동으로 빼지 않는다. 확인 화면에서 사용자가 결정한다(규칙 9, 12).
 */
public enum ReviewUnitVerdict {
    /** 아직 검수하지 않았다. */
    PENDING,
    /** 복습 가치와 근거 연결 모두 기준 이상의 신뢰도로 통과했다. */
    APPROVED,
    /** 신뢰도가 기준보다 낮아 판정을 보류했다. 사용자가 확인한다(규칙 5). */
    HELD,
    /** 복습 가치가 없거나 근거 연결이 맞지 않다고 판정했다. 확인 화면에서 제외를 추천한다. */
    REJECTED,
    /** Jev 호출이 끝내 실패했다. 사용자가 확인한다. */
    UNAVAILABLE
}
