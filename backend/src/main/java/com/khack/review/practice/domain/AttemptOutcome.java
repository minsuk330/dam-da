package com.khack.review.practice.domain;

/**
 * 시도 결과 (스펙 §6.2, §6.4.5). 단계적 피드백이 읽는 입력이며 제출 응답에도 실린다. 따로 저장하지 않고 저장된 답변 판정
 * ({@link AnswerJudgment})에서 {@code AttemptOutcomes}가 도출한다. 피드백은 이 값만 보고 FSRS 등급은 바꾸지 않는다.
 */
public enum AttemptOutcome {
    /** 정답 기준 충족 (verdict met). */
    CORRECT,
    /** 정답 기준 미충족 (verdict not_met). */
    WRONG,
    /** 판정 불가 또는 신뢰도 부족. 기억 상태를 바꾸지 않았으므로 피드백도 사용자 확인으로 넘긴다. */
    UNCERTAIN,
    /** 문제가 모호해 보류됐다({@code HoldReason.questionNeedsRecheck}). 같은 문제 대신 변형 문제로 다시 확인한다. */
    QUESTION_AMBIGUOUS
}
