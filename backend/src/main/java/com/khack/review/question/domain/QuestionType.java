package com.khack.review.question.domain;

/**
 * 문제 유형 (스펙 §6.4.6, §7.8). {@code ladderLevel}은 안정도 사다리 단계다: 1 객관식, 2 단답·서술, 3 사례 판단·응용.
 * 오류 찾기는 헷갈린 지점 항목 전용이며 2단계부터 쓴다.
 */
public enum QuestionType {
    MULTIPLE_CHOICE(1),
    SHORT_ANSWER(2),
    ESSAY(2),
    ERROR_FINDING(2),
    CASE_JUDGMENT(3),
    CASE_APPLICATION(3);

    private final int ladderLevel;

    QuestionType(int ladderLevel) {
        this.ladderLevel = ladderLevel;
    }

    public int ladderLevel() {
        return ladderLevel;
    }
}
