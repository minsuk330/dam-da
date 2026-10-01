package com.khack.review.analysis.domain;

import java.util.Map;
import java.util.Set;

/**
 * 학습 세션의 진행 상태 (스펙 §7 2단계, 페르소나와 도메인 스토리 S1). 정해진 다음 상태로만 넘어간다.
 */
public enum LearningSessionStatus {
    /** 대화를 받아 세션을 만들었다. */
    RECEIVED,
    /** Jev가 복습 단위를 검수하고 있다. */
    REVIEWING,
    /** 사용자가 앱에서 내용을 확인하기를 기다린다. */
    AWAITING_CONFIRMATION,
    /** 사용자가 확인을 마쳤다. 초기 평가와 문제 생성은 이 이후다(규칙 12, 18). */
    CONFIRMED,
    /** 첫 학습 문제가 준비됐다. */
    QUESTIONS_READY,
    /** 학습을 시작했다. */
    IN_PROGRESS;

    private static final Map<LearningSessionStatus, Set<LearningSessionStatus>> NEXT = Map.of(
            RECEIVED, Set.of(REVIEWING),
            REVIEWING, Set.of(AWAITING_CONFIRMATION),
            AWAITING_CONFIRMATION, Set.of(CONFIRMED),
            CONFIRMED, Set.of(QUESTIONS_READY),
            QUESTIONS_READY, Set.of(IN_PROGRESS),
            IN_PROGRESS, Set.of());

    public boolean canMoveTo(LearningSessionStatus next) {
        return NEXT.get(this).contains(next);
    }
}
