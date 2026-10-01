package com.khack.review.question.application.port.out;

/**
 * 복습 단위 1개의 문제들을 한 번에 만든다 (스펙 §6.1, §7.8). 구현(LLM 프롬프트)은 ai 담당이다.
 *
 * <p>계약
 * <ul>
 *   <li>요청한 대상마다 문제를 정확히 하나씩, 대상의 {@code type}대로 만든다. 객관식이면 보기와 정답 번호를, 아니면 빈 보기와 null.</li>
 *   <li>문제와 정답 기준은 요청의 기억 항목·핵심 사실·근거 발화에서만 나온다. 대화에 없는 사실을 정답으로 삼지 않는다(규칙 8).</li>
 *   <li>문제 하나는 대상 항목 하나만 확인한다. 단위의 다른 핵심 사실은 배경과 오답 보기 재료로만 쓴다.
 *       같은 요청의 문제끼리 서로 답의 단서가 되지 않게 한다.</li>
 *   <li>오류 찾기({@code ERROR_FINDING})는 사용자가 믿었던 내용({@code itemContent})을 제시하고, 정답 기준은 대화 속 교정({@code correction})이다.</li>
 *   <li>{@code avoidStems}가 있으면 그와 다른 표현·상황의 문제를 만든다(재생성·변형 문제).</li>
 *   <li>일부 대상만 실패하면 그 대상만 {@link UnitQuestionResult.TargetResult#failed}로 돌려준다.
 *       요청 전체를 처리할 수 없으면 {@link QuestionGenerationException}. 품질 검사와 재생성은 서버가 한다.</li>
 * </ul>
 */
public interface QuestionGenerator {

    UnitQuestionResult generate(UnitQuestionRequest request);
}
