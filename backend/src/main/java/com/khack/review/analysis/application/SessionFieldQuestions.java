package com.khack.review.analysis.application;

import com.khack.review.analysis.application.FieldTaxonomy.Field;
import com.khack.review.analysis.application.FieldTaxonomy.Subfield;
import com.khack.review.common.application.port.out.JevQuestion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 학습 분야 판정 Jev 질문 정의 (스펙 §7.10, docs/jev.md). 질문 문장·선택지 설명은 프롬프트이므로 ai 소유다.
 * 선택지 키는 분류표 코드다. 판정({@link SessionFieldClassifier})은 질문 이름과 코드에만 의존한다. 상태 필드는 {@link SessionFieldState}.
 */
public final class SessionFieldQuestions {

    /** choice: 대분류. */
    public static final String FIELD = "field";

    /** choice: 고른 대분류 안의 소분류. */
    public static final String SUBFIELD = "subfield";

    private SessionFieldQuestions() {
    }

    public static Map<String, JevQuestion> field(FieldTaxonomy taxonomy) {
        Map<String, @Nullable String> options = new LinkedHashMap<>();
        for (Field field : taxonomy.fields()) {
            options.put(field.code(), field.label() + " — " + field.subfields().stream()
                    .filter(subfield -> !subfield.code().equals(FieldTaxonomy.fallbackFor(field.code())))
                    .map(Subfield::label)
                    .collect(Collectors.joining(", ")));
        }
        return Map.of(FIELD, JevQuestion.choice(
                "`topic`과 `unitTitles`는 사용자가 AI와 나눈 학습 대화 하나의 주제와 복습 단위 제목이다. "
                        + "이 대화가 주로 공부한 학문 분야는 무엇인가? 여러 분야에 걸치면 복습 단위가 가장 많이 다루는 분야를 고른다. "
                        + "어느 분야에도 맞지 않으면 `etc`를 고른다.",
                options));
    }

    public static Map<String, JevQuestion> subfield(Field field) {
        Map<String, @Nullable String> options = new LinkedHashMap<>();
        for (Subfield subfield : field.subfields()) {
            options.put(subfield.code(), subfield.hint() == null ? subfield.label() : subfield.label() + " — " + subfield.hint());
        }
        return Map.of(SUBFIELD, JevQuestion.choice(
                "`topic`과 `unitTitles`는 사용자가 AI와 나눈 학습 대화 하나의 주제와 복습 단위 제목이고, 이 대화는 `field` 분야다. "
                        + "이 대화가 주로 공부한 세부 분야는 무엇인가? 여러 세부 분야에 걸치면 복습 단위가 가장 많이 다루는 것을 고른다. "
                        + "맞는 세부 분야가 없을 때만 `" + FieldTaxonomy.fallbackFor(field.code()) + "`를 고른다.",
                options));
    }
}
