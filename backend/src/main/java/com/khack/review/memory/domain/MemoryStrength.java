package com.khack.review.memory.domain;

/**
 * 기억 강도: 학습 내용을 "얼마나 오래 유지할지" (스펙 §6.4.7). FSRS 목표 유지율과 매일 학습의 문제 유형 상한을 정한다.
 * 문제 유형 단계는 §6.4.6 사다리의 1 객관식, 2 단답·서술, 3 사례 판단·응용이다.
 */
public enum MemoryStrength {
    LIGHT("가볍게 기억", 0.80, 2),
    UNDERSTAND("개념 이해", 0.85, 2),
    APPLY("실무 적용", 0.90, 3),
    MASTER("완전 숙달", 0.95, 3);

    private final String label;
    private final double desiredRetention;
    private final int maxQuestionLevel;

    MemoryStrength(String label, double desiredRetention, int maxQuestionLevel) {
        this.label = label;
        this.desiredRetention = desiredRetention;
        this.maxQuestionLevel = maxQuestionLevel;
    }

    public String label() {
        return label;
    }

    public double desiredRetention() {
        return desiredRetention;
    }

    public int maxQuestionLevel() {
        return maxQuestionLevel;
    }
}
