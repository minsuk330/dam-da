package com.khack.review.question.application.port.out;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 생성된 문제 후보. 품질 검사(Jev)를 통과하기 전에는 사용자에게 보이지 않는다(규칙 3).
 *
 * @param stem           문제 문장
 * @param choices        객관식 보기. 객관식이 아니면 비어 있다
 * @param correctChoice  객관식 정답 보기의 0부터 시작하는 번호. 객관식이 아니면 null
 * @param answerCriteria 정답 기준: 답변이 갖춰야 할 필수 요소 1~3개. Jev 답변 판정(§6.4.5)이 이 목록으로 met/not_met을 정한다
 * @param modelAnswer    모범 답안
 * @param hint           정답을 말하지 않고 떠올릴 방향만 알려주는 한 문장 (단계적 피드백 §7 5단계)
 * @param explanation    틀렸을 때 보여줄 개념 설명 (단계적 피드백)
 * @param evidenceTurns  문제와 정답의 근거 발화 index (규칙 2, 15)
 */
public record GeneratedQuestion(
        String stem,
        List<String> choices,
        @Nullable Integer correctChoice,
        List<String> answerCriteria,
        String modelAnswer,
        @Nullable String hint,
        @Nullable String explanation,
        List<Integer> evidenceTurns) {
}
