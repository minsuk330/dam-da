package com.khack.review.question.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 문제 생성의 입력. 사용자가 앱에서 확인을 마친 발화와 복습 단위만 넘긴다(스펙 규칙 12).
 * 수집 컨텍스트의 타입을 직접 참조하지 않도록 필요한 값만 옮긴 것이다.
 */
public record QuestionSource(String sessionId, List<Turn> turns, List<Unit> units) {

    /** {@code meta} 발화는 근거가 될 수 없다(스펙 규칙 15). */
    public record Turn(int index, String text, boolean meta) {
    }

    public record Unit(String title, List<KeyPoint> keyPoints, List<Confusion> confusions) {
    }

    /** {@code turns}는 이 내용을 설명한 AI 답변 직전의 사용자 발화 index다. */
    public record KeyPoint(String point, PointKind kind, List<Integer> turns) {
    }

    /** {@code correction}은 {@code turn} 발화에 대한 대화 속 AI의 교정이다. */
    public record Confusion(int turn, String userBelief, @Nullable String correction) {
    }
}
