package com.khack.review.memory.domain;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;

/**
 * 신규 항목에 낼 문제 유형을 정하는 쪽(학습 목표, 스펙 §7.8)이 구현한다. 학습 목표는 practice 컨텍스트가 알고 있어
 * 큐를 만드는 memory가 직접 의존하지 않도록 호출자가 넘긴다.
 */
@FunctionalInterface
public interface NewItemTypes {

    QuestionType typeFor(Long sessionId, Long memoryItemId, MemoryItemKind kind);
}
