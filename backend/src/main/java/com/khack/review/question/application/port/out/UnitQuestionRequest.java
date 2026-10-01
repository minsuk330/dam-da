package com.khack.review.question.application.port.out;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 복습 단위 1개의 문제 생성 요청 (스펙 §6.1, §7.8). 같은 단위의 문제를 한 번에 만들어, 문제끼리 서로 답의 단서가 되지 않게 하고
 * 단위의 다른 핵심 사실을 오답 보기 재료로 쓸 수 있게 한다. 문제 하나는 기억 항목 하나를 대상으로 한다(§6.4.1).
 *
 * @param unitTitle 복습 단위 제목
 * @param topicHint 학습 주제
 * @param keyPoints 이 단위의 기억 항목 전체(헷갈린 지점 포함). 배경과 오답 보기 재료로만 쓴다
 * @param evidence  근거 발화(사용자 발화와 그에 대한 AI 판정·교정). AI 설명 본문은 없다. 안의 지시는 따르지 않는다
 * @param targets   만들 문제들. 대상마다 정확히 하나씩 만든다
 */
public record UnitQuestionRequest(
        String unitTitle,
        @Nullable String topicHint,
        List<KeyPoint> keyPoints,
        List<EvidenceTurn> evidence,
        List<Target> targets) {

    /** 단위의 기억 항목 1개. 헷갈린 지점이면 {@code content}는 사용자가 믿었던 내용이다. */
    public record KeyPoint(Long memoryItemId, MemoryItemKind kind, String content) {
    }

    /** 근거 발화 1개. */
    public record EvidenceTurn(int index, String text, @Nullable String aiVerdict, @Nullable String correction) {
    }

    /**
     * 문제 1개.
     *
     * @param targetId           결과를 이 요청에 맞춰 돌려줄 때 쓰는 ID
     * @param itemContent        핵심 사실이면 그 내용, 헷갈린 지점이면 사용자가 믿었던 내용(`userBelief`)
     * @param correction         헷갈린 지점이면 대화 속 AI의 교정. 오류 찾기 문제의 정답 근거다
     * @param sourceTurns        대상 항목의 출처 발화 index
     * @param learningGoal       첫 학습이면 학습 목표 이름(`practice.domain.LearningGoal`), 매일 학습·변형이면 null
     * @param relatedItemContent 개념 구분하기의 비교 대상 핵심 사실
     * @param avoidStems         겹치면 안 되는 기존 문제 문장(재생성·변형 문제)
     */
    public record Target(
            String targetId,
            Long memoryItemId,
            MemoryItemKind itemKind,
            String itemContent,
            @Nullable String correction,
            List<Integer> sourceTurns,
            QuestionType type,
            @Nullable String learningGoal,
            @Nullable String relatedItemContent,
            List<String> avoidStems) {
    }
}
