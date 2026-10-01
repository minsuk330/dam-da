package com.khack.review.question.application.port.out;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 문제 1개 생성 요청 (스펙 §6.1, §7.8). 문제는 기억 항목 1개를 대상으로 한다(§6.4.1).
 *
 * @param itemContent 핵심 사실이면 그 내용, 헷갈린 지점이면 사용자가 믿었던 내용(`userBelief`)
 * @param correction 헷갈린 지점이면 대화 속 AI의 교정. 오류 찾기 문제의 정답 근거다
 * @param evidence 근거 발화(사용자 발화와 그에 대한 AI 판정·교정). AI 설명 본문은 없다
 * @param learningGoal 첫 학습이면 학습 목표 이름(`practice.domain.LearningGoal`), 매일 학습·변형이면 null
 * @param relatedItemContent 개념 구분하기의 비교 대상 핵심 사실
 * @param avoidStems 변형 문제일 때 겹치면 안 되는 기존 문제 문장
 */
public record QuestionRequest(
        Long memoryItemId,
        MemoryItemKind itemKind,
        String itemContent,
        @Nullable String correction,
        String unitTitle,
        @Nullable String topicHint,
        List<EvidenceTurn> evidence,
        @Nullable String learningGoal,
        QuestionType type,
        @Nullable String relatedItemContent,
        List<String> avoidStems) {

    /** 근거 발화 1개. */
    public record EvidenceTurn(int index, String text, @Nullable String aiVerdict, @Nullable String correction) {
    }
}
