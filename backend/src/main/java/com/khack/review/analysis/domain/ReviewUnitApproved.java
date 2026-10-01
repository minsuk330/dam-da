package com.khack.review.analysis.domain;

/**
 * Jev가 복습 단위의 복습 가치와 근거 연결을 통과시켰다(스펙 §8.4 `ReviewUnitApproved`). 검수 결과 저장과 같은 트랜잭션에서 발행된다.
 */
public record ReviewUnitApproved(Long sessionId, Long reviewUnitId) {
}
