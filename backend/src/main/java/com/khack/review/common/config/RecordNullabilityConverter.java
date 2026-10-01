package com.khack.review.common.config;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 응답 DTO record의 null 여부를 API 계약에 옮긴다. springdoc은 jspecify {@link Nullable}을 읽지 않아
 * 모든 필드가 선택값이 된다. Jackson은 null도 키를 빼지 않고 내보내므로 모든 컴포넌트를 required로 두고,
 * {@code @Nullable} 컴포넌트에만 null 타입을 더한다.
 */
@Component
class RecordNullabilityConverter implements ModelConverter {

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        Class<?> raw = rawClass(type);
        if (resolved == null || raw == null || !raw.isRecord()) {
            return resolved;
        }
        Schema model = resolved.get$ref() == null ? resolved
                : context.getDefinedModels().get(resolved.get$ref().substring(resolved.get$ref().lastIndexOf('/') + 1));
        if (model == null || model.getProperties() == null) {
            return resolved;
        }
        for (RecordComponent component : raw.getRecordComponents()) {
            Schema property = (Schema) model.getProperties().get(component.getName());
            if (property == null) {
                continue;
            }
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)
                    && (property.getTypes() == null || !property.getTypes().contains("null"))) {
                property.addType("null");
            }
            if (model.getRequired() == null || !model.getRequired().contains(component.getName())) {
                model.addRequiredItem(component.getName());
            }
        }
        return resolved;
    }

    private static @Nullable Class<?> rawClass(AnnotatedType type) {
        if (type.getType() instanceof Class<?> clazz) {
            return clazz;
        }
        JavaType javaType = Json.mapper().constructType(type.getType());
        return javaType == null ? null : javaType.getRawClass();
    }
}
