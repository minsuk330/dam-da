package com.khack.review.collection.domain;

import com.khack.review.common.json.Json;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;

/** 아직 분석 컨텍스트가 엔티티로 다루지 않는 값은 커넥터가 보낸 모양 그대로 JSON 문자열로 보관한다. */
final class JsonListConverters {

    private JsonListConverters() {
    }

    @Converter
    static class ReviewUnits implements AttributeConverter<List<ReviewUnit>, String> {

        private static final TypeReference<List<ReviewUnit>> TYPE = new TypeReference<>() {
        };

        @Override
        public String convertToDatabaseColumn(List<ReviewUnit> units) {
            return Json.MAPPER.writeValueAsString(units == null ? List.of() : units);
        }

        @Override
        public List<ReviewUnit> convertToEntityAttribute(String json) {
            return json == null ? List.of() : Json.MAPPER.readValue(json, TYPE);
        }
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
}
