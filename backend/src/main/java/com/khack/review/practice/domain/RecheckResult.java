package com.khack.review.practice.domain;

/** 모호 보류 문제 재검사 결과. */
public enum RecheckResult {
    /** 품질 검사를 다시 통과해 그대로 쓴다. */
    PASSED,
    /** 떨어져 폐기했다. 변형 문제를 만들었으면 그것을 쓴다. */
    RETIRED,
    /** 재검사 호출이 실패했다. 피드백 요청에서 한 번 더 시도한다. */
    FAILED
}
