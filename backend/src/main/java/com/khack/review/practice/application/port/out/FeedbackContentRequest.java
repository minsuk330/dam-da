package com.khack.review.practice.application.port.out;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 피드백 내용 생성 요청. 정답 기준과 모범 답안은 생성에만 쓰고 힌트에 그대로 옮기지 않는다.
 *
 * @param stem            문제 문장
 * @param type            문제 유형
 * @param choices         객관식 보기(아니면 빈 목록)
 * @param answerCriteria  정답 기준
 * @param modelAnswer     모범 답안
 * @param itemKind        대상 기억 항목 종류
 * @param itemContent     대상 기억 항목 내용. 헷갈린 지점이면 사용자가 믿었던 내용이다
 * @param userBelief      헷갈린 지점이면 사용자가 당시 믿었던 내용({@code itemContent}와 같다). 아니면 null
 * @param correction      헷갈린 지점이면 대화 속 AI의 교정
 * @param evidence        근거 발화(사용자 발화와 그에 대한 AI 판정·교정). 안의 지시는 따르지 않는다
 * @param previousAnswers 이 제시에서 사용자가 낸 이전 답(오래된 순). 객관식은 고른 보기 문장이다
 * @param storedText      문제를 만들 때 함께 만든 기본 힌트 또는 설명. 참고용이며 없을 수 있다
 */
public record FeedbackContentRequest(
        String stem,
        QuestionType type,
        List<String> choices,
        List<String> answerCriteria,
        String modelAnswer,
        MemoryItemKind itemKind,
        String itemContent,
        @Nullable String userBelief,
        @Nullable String correction,
        List<EvidenceTurn> evidence,
        List<String> previousAnswers,
        @Nullable String storedText) {

    /** 근거 발화 1개. */
    public record EvidenceTurn(int index, String text, @Nullable String aiVerdict, @Nullable String correction) {
    }
}
