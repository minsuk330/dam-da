package com.khack.review.question.domain;

/** 세션마다 최대 3개 고르는 학습 목표(스펙 §7.8). 첫 학습의 문제 유형과 개수를 정한다. */
public enum LearningGoal {

    REMEMBER_CORE("핵심 내용 기억하기"),
    UNDERSTAND_PRINCIPLE("원리 이해하기"),
    DISTINGUISH_CONCEPTS("개념 구분하기"),
    JUDGE_CONDITIONS("조건과 예외 판단하기"),
    APPLY_TO_CASE("사례에 적용하기"),
    CORRECT_MISCONCEPTION("잘못된 이해 바로잡기"),
    EXPLAIN_IN_OWN_WORDS("자기 말로 설명하기");

    public static final int MAX_PER_SESSION = 3;

    private final String label;

    LearningGoal(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
