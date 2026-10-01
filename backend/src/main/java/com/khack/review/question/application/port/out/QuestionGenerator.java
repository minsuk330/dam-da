package com.khack.review.question.application.port.out;

/**
 * 기억 항목 1개에 대한 문제를 만든다 (스펙 §6.1, §7.8). 구현(LLM 프롬프트)은 ai 담당이다.
 *
 * <p>계약
 * <ul>
 *   <li>요청한 {@code type}의 문제를 만든다. 객관식이면 보기와 정답 번호를, 아니면 빈 보기와 null을 돌려준다.</li>
 *   <li>문제와 정답 기준은 요청의 기억 항목과 근거 발화에서만 나온다. 대화에 없는 사실을 정답으로 삼지 않는다(규칙 8).</li>
 *   <li>오류 찾기({@code ERROR_FINDING})는 사용자가 믿었던 내용({@code itemContent})을 제시하고, 정답 기준은 대화 속 교정({@code correction})이다.</li>
 *   <li>{@code avoidStems}가 있으면 그와 다른 표현·상황의 문제를 만든다(변형 문제).</li>
 *   <li>만들 수 없으면 {@link QuestionGenerationException}. 서버는 품질 검사와 재생성을 따로 한다.</li>
 * </ul>
 */
public interface QuestionGenerator {

    GeneratedQuestion generate(QuestionRequest request);
}
