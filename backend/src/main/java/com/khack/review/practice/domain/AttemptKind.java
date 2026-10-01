package com.khack.review.practice.domain;

/** 시도 구분 (스펙 §6.4.5, §6.4.8). FSRS 등급은 {@link #isEvaluated()}인 시도로만 정한다. */
public enum AttemptKind {
    /** 복습 기회의 첫 무도움 시도. */
    FIRST_UNAIDED(true),
    /** 힌트·설명을 본 뒤의 재시도. 지식 상태 표시와 피드백에만 쓴다. */
    AFTER_AID(false),
    /** 같은 날 다른 문제 몇 개 뒤에 다시 묻는 지연된 무도움 재확인. 별도의 같은 날 복습으로 평가한다. */
    DELAYED_RECHECK(true);

    private final boolean evaluated;

    AttemptKind(boolean evaluated) {
        this.evaluated = evaluated;
    }

    public boolean isEvaluated() {
        return evaluated;
    }
}
