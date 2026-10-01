package com.khack.review.question.domain;

/** 문제 유형(스펙 §6.4.6, §7.8). 이 목록 밖의 유형은 만들지 않는다. */
public enum QuestionType {
    MULTIPLE_CHOICE, SHORT_ANSWER, DESCRIPTIVE, CASE_JUDGMENT, CASE_APPLICATION, ERROR_FINDING
}
