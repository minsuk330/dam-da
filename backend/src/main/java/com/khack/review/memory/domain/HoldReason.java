package com.khack.review.memory.domain;

/** 등급 변환을 보류한 이유 (스펙 §6.4.5 변환표 행 1~3). 보류하면 기억 상태를 바꾸지 않는다. */
public enum HoldReason {
    /** 행 1: `verdict` 신뢰도가 기준 미만. */
    LOW_CONFIDENCE,
    /** 행 1: 판정 불가. */
    UNABLE_TO_JUDGE,
    /** 행 2: 질문을 다르게 이해했음이 기준 이상의 신뢰도로 확인됨. */
    MISREAD,
    /** 행 3: 정답이지만 추측 의심이 자기평가로 해소되지 않음. */
    GUESS_UNCONFIRMED;

    /** 문제가 모호해서 보류했다. 같은 문제를 다시 내지 않고 품질 검사로 돌려보낸 뒤 수정·변형한 문제로 다시 확인한다. */
    public boolean questionNeedsRecheck() {
        return this == UNABLE_TO_JUDGE || this == MISREAD;
    }

    /** 근거를 묻는 짧은 질문으로 확인한다. */
    public boolean asksForReason() {
        return this == GUESS_UNCONFIRMED;
    }
}
