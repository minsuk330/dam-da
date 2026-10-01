package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.FieldSource;

/**
 * 세션의 학습 분야 라벨 (스펙 §7.10). {@code code}·{@code label}은 소분류, {@code fieldCode}·{@code fieldLabel}은 그 대분류다.
 * 분류표에서 사라진 코드는 코드를 이름으로 쓴다.
 */
public record FieldLabel(String code, String label, String fieldCode, String fieldLabel, FieldSource source) {
}
