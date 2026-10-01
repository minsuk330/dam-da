package com.khack.review.practice.domain;

/** 모호 보류 문제 재검사 결과. */
public enum RecheckResult {
    /** 품질 검사를 다시 통과했다. 문제는 승인으로 남지만, 보류된 자리의 다시 확인은 변형 문제로 한다. */
    PASSED,
    /** 떨어져 폐기했다. 변형 문제를 만들었으면 그것을 쓴다. */
    RETIRED,
    /** 재검사 호출이 실패했다. 피드백 요청에서 한 번 더 시도한다. */
    FAILED
}
