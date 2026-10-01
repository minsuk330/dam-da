package com.khack.review.practice.domain;

/**
 * 풀이 중 보여준 도움 (스펙 §6.4.5 평가 대상 시도). 질문 문구를 풀어 준 해석 도움은 내용 힌트가 아니므로,
 * 해석 도움 뒤의 첫 답은 무도움 시도로 본다.
 */
public enum AidType {
    INTERPRETATION(false),
    HINT(true),
    EXPLANATION(true);

    private final boolean content;

    AidType(boolean content) {
        this.content = content;
    }

    /** 답의 내용을 알려 주는 도움인가. 이 도움을 본 뒤의 답은 평가하지 않는다. */
    public boolean isContent() {
        return content;
    }
}
