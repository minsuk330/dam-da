package com.khack.review.practice.domain;

/** 학습 목표: "무엇을 확인할지" (스펙 §7.8). 세션마다 최대 3개를 고르며, 첫 학습의 문제 유형과 개수를 정한다. */
public enum LearningGoal {
    KEY_RECALL(1, "핵심 내용 기억하기", "정의, 용어, 규칙을 단서 없이 떠올린다."),
    PRINCIPLE(2, "원리 이해하기", "결과가 발생하는 이유와 작동 과정을 이해한다."),
    DISTINGUISH(3, "개념 구분하기", "비슷한 개념의 차이를 구분한다."),
    CONDITION(4, "조건과 예외 판단하기", "어떤 조건에서 규칙이 적용되거나 달라지는지 판단한다."),
    APPLY_CASE(5, "사례에 적용하기", "배운 내용을 새로운 상황이나 문제에 적용한다."),
    CORRECT_MISCONCEPTION(6, "잘못된 이해 바로잡기", "이전에 가졌던 오개념이나 불완전한 이해를 교정한다."),
    EXPLAIN_OWN_WORDS(7, "자기 말로 설명하기", "외운 문장이 아니라 자신의 언어로 개념을 설명한다.");

    public static final int MAX_SELECTED = 3;

    private final int number;
    private final String label;
    private final String description;

    LearningGoal(int number, String label, String description) {
        this.number = number;
        this.label = label;
        this.description = description;
    }

    public int number() {
        return number;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
