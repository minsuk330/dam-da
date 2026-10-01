package com.khack.review.question.domain;

/** 문제 대상인 기억 항목의 종류(스펙 §6.4.1). 핵심 사실의 kind 3종과 헷갈린 지점이다. */
public enum ItemKind {
    FACT, WARNING, PRACTICE, CONFUSION
}
