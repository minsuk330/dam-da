package com.khack.review.memory.domain;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import org.jspecify.annotations.Nullable;

/**
 * 안정도(S)에 따른 문제 유형 사다리 (스펙 §6.4.6). S가 클수록 높은 단계의 문제를 내되, 세션의 기억 강도가 정한 상한(§6.4.7)을 넘지 않는다.
 * 단계: 1 객관식, 2 단답·서술, 3 사례 판단·응용. 헷갈린 지점 항목은 2단계부터 오류 찾기를 쓴다.
 * 경계값(S 일수)은 초기안이며 설정으로 조정한다.
 */
public record QuestionLadder(double level2MinStabilityDays, double level3MinStabilityDays) {

    public QuestionLadder {
        if (level2MinStabilityDays <= 0 || level3MinStabilityDays < level2MinStabilityDays) {
            throw new IllegalArgumentException("사다리 경계값은 0 < 2단계 <= 3단계여야 합니다.");
        }
    }

    /** S로 정한 단계(상한 적용 전). 안정도를 모르면(등급을 받은 적 없는 보류 항목 등) 1단계다. */
    public int level(@Nullable Double stabilityDays) {
        if (stabilityDays == null || stabilityDays < level2MinStabilityDays) {
            return 1;
        }
        return stabilityDays < level3MinStabilityDays ? 2 : 3;
    }

    /** 복습 항목에 낼 문제 유형. {@code maxLevel}은 기억 강도의 상한이다. */
    public QuestionType typeFor(@Nullable Double stabilityDays, MemoryItemKind kind, int maxLevel) {
        int level = Math.min(level(stabilityDays), maxLevel);
        if (level <= 1) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        if (kind == MemoryItemKind.CONFUSION) {
            return QuestionType.ERROR_FINDING;
        }
        if (level == 2) {
            return QuestionType.SHORT_ANSWER;
        }
        return kind == MemoryItemKind.WARNING ? QuestionType.CASE_JUDGMENT : QuestionType.CASE_APPLICATION;
    }
}
