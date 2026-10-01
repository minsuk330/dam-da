package com.khack.review.practice.domain;

/** 누가 채점했나. 객관식은 Jev 없이 코드가 채점한다(스펙 §6.4.5). */
public enum JudgedBy {
    JEV,
    CODE
}
