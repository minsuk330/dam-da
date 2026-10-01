package com.khack.review.practice.domain;

/** 학습 목표: "무엇을 확인할지" (스펙 §7.8). 세션마다 최대 3개를 고르며, 첫 학습의 문제 유형과 개수를 정한다. */
public enum LearningGoal {
    KEY_RECALL(1, "핵심 내용 기억하기", "정의·용어·규칙을 단서 없이 떠올려요."),
    PRINCIPLE(2, "원리 이해하기", "결과가 생기는 이유와 작동 과정을 이해해요."),
    DISTINGUISH(3, "개념 구분하기", "비슷한 개념의 차이를 구분해요."),
    CONDITION(4, "조건과 예외 판단하기", "어떤 조건에서 규칙이 적용되거나 달라지는지 판단해요."),
    APPLY_CASE(5, "사례에 적용하기", "배운 내용을 새로운 상황이나 문제에 적용해요."),
    CORRECT_MISCONCEPTION(6, "잘못된 이해 바로잡기", "전에 잘못 알았거나 덜 이해한 부분을 바로잡아요."),
    EXPLAIN_OWN_WORDS(7, "자기 말로 설명하기", "외운 문장이 아니라 내 말로 개념을 설명해요.");

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
