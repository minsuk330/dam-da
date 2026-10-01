package com.khack.review.analysis.application;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 학습 분야 판정에서 Jev에 보내는 상태 (스펙 §7.10). {@link SessionFieldQuestions}가 필드 이름을 가리킨다.
 * {@code field}는 2단계(소분류)에서만 1단계에서 고른 대분류 이름을 담는다.
 */
public record SessionFieldState(@Nullable String topic, List<String> unitTitles, @Nullable String field) {

    public SessionFieldState withField(String fieldLabel) {
        return new SessionFieldState(topic, unitTitles, fieldLabel);
    }
}
