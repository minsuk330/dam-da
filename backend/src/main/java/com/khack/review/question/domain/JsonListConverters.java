package com.khack.review.question.domain;

import com.khack.review.common.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;

/** 짧은 목록(선택지, 정답 기준, 근거 발화 index)을 JSON 문자열 컬럼 하나에 담는다. */
final class JsonListConverters {

    private JsonListConverters() {
    }

    @Converter
    static class Strings implements AttributeConverter<List<String>, String> {

        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            return Json.MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            return dbData == null ? List.of() : Json.MAPPER.readValue(dbData, new TypeReference<List<String>>() {
            });
        }
    }

    @Converter
    static class Integers implements AttributeConverter<List<Integer>, String> {

        @Override
        public String convertToDatabaseColumn(List<Integer> attribute) {
            return Json.MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        }

        @Override
        public List<Integer> convertToEntityAttribute(String dbData) {
            return dbData == null ? List.of() : Json.MAPPER.readValue(dbData, new TypeReference<List<Integer>>() {
            });
        }
    }
}
