package com.khack.review.memory.domain;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;

/**
 * 신규 항목을 받을 세션과 낼 문제 유형을 정하는 쪽(첫 학습·학습 목표, 스펙 §6.4.4, §7.8)이 구현한다. 첫 학습과 학습 목표는
 * practice 컨텍스트가 알고 있어 큐를 만드는 memory가 직접 의존하지 않도록 호출자가 넘긴다.
 */
public interface NewItems {

    /**
     * 이 세션의 항목을 신규로 받는가. 첫 학습을 끝낸 세션만 받는다. 첫 학습 전·도중인 세션의 항목은 첫 학습에서 다루므로
     * 같은 날 매일 학습에 다시 내지 않는다. 그래서 신규는 첫 학습 상한으로 다루지 못한 항목이다.
     */
    boolean admits(Long sessionId);

    QuestionType typeFor(Long sessionId, Long memoryItemId, MemoryItemKind kind);
}
