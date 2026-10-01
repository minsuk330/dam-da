package com.khack.review.memory.domain;

/**
 * 풀이 시도의 종류 (스펙 §6.4.5 평가 대상 시도, §6.4.8). 질문 문구를 풀어 준 해석 도움은 내용 힌트가 아니므로,
 * 해석 도움 뒤의 첫 답은 {@link #FIRST_UNASSISTED}다.
 */
public enum AttemptKind {
    /** 복습 기회의 첫 무도움 시도. 평가한다. */
    FIRST_UNASSISTED,
    /** 힌트·설명을 본 직후의 재시도. 평가하지 않는다. */
    ASSISTED_RETRY,
    /** 지연된 무도움 재확인. 별도의 같은 날 복습으로 평가한다. */
    DELAYED_RECHECK
}
