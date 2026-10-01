package com.khack.review.question.domain;

import com.khack.review.common.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;

/** 문제의 짧은 목록 값(보기, 정답 기준, 근거 발화)을 JSON 문자열로 보관한다. */
final class JsonLists {

    private JsonLists() {
    }

    @Converter
    static class Strings implements AttributeConverter<List<String>, String> {

        private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
        };

        @Override
        public String convertToDatabaseColumn(List<String> values) {
            return Json.MAPPER.writeValueAsString(values == null ? List.of() : values);
        }

        @Override
        public List<String> convertToEntityAttribute(String json) {
            return json == null ? List.of() : Json.MAPPER.readValue(json, TYPE);
        }
    }

    @Converter
    static class Integers implements AttributeConverter<List<Integer>, String> {

        private static final TypeReference<List<Integer>> TYPE = new TypeReference<>() {
        };

        @Override
        public String convertToDatabaseColumn(List<Integer> values) {
            return Json.MAPPER.writeValueAsString(values == null ? List.of() : values);
        }

        @Override
        public List<Integer> convertToEntityAttribute(String json) {
            return json == null ? List.of() : Json.MAPPER.readValue(json, TYPE);
        }
    }
}
