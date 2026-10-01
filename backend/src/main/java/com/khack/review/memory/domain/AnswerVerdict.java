package com.khack.review.memory.domain;

/** 답변 판정 결과 (스펙 §6.4.5 Jev 출력 `verdict`). 객관식은 코드가 채점해 MET·NOT_MET만 쓴다. */
public enum AnswerVerdict {
    /** 필수 기준을 모두 충족하고 모순 없음. */
    MET,
    /** 필수 내용 누락 또는 틀린 내용 포함. */
    NOT_MET,
    /** 정보 부족·모호성 등으로 판정 불가. */
    UNABLE_TO_JUDGE
}
