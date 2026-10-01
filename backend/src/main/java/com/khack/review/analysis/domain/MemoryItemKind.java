package com.khack.review.analysis.domain;

/** 기억 항목의 출처 (스펙 §6.4.1). 핵심 사실의 kind 3종과 헷갈린 지점. */
public enum MemoryItemKind {
    FACT, WARNING, PRACTICE, CONFUSION
}
