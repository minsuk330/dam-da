package com.khack.review.practice.application.port.out;

/**
 * 단계적 피드백(스펙 §7 5단계)의 힌트·개념 설명과 선행 개념 제안을 만든다. 구현(LLM 프롬프트)은 ai 담당이다.
 * 무엇을 언제 보여 줄지(힌트 → 재도전 → 설명 → 확인 문제)는 서버가 정하고, 여기서는 내용만 만든다.
 *
 * <p>계약
 * <ul>
 *   <li>내용은 요청의 문제·정답 기준·기억 항목·교정·근거 발화에서만 나온다. 대화에 없는 사실을 만들지 않는다(규칙 8).</li>
 *   <li>힌트는 정답을 말하지 않고 떠올릴 방향만 알려 준다. 대화 속 AI 교정({@code correction})과 핵심 사실에서 먼저 만든다.</li>
 *   <li>개념 설명은 사용자가 당시 믿었던 내용({@code userBelief})이 있으면 그것과 비교해 보여 주고, 쓴 근거 발화의 index를
 *       {@link FeedbackContent#evidenceTurns}로 돌려준다. 이전 답({@code previousAnswers})의 오류를 짚을 수 있다.</li>
 *   <li>선행 개념 제안은 이 항목을 이해하려면 먼저 알아야 하는 개념 하나와 이유다.</li>
 *   <li>만들 수 없으면 {@link FeedbackGenerationException}. 서버는 문제에 저장된 기본 힌트·설명으로 대신한다.</li>
 * </ul>
 */
public interface FeedbackContentGenerator {

    FeedbackContent hint(FeedbackContentRequest request);

    FeedbackContent explanation(FeedbackContentRequest request);

    PrerequisiteSuggestion prerequisite(FeedbackContentRequest request);
}
