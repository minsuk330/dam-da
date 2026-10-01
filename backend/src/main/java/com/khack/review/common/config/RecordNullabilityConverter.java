package com.khack.review.common.config;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 응답 DTO record의 null 여부를 API 계약에 옮긴다. springdoc은 jspecify {@link Nullable}을 읽지 않아
 * 모든 필드가 선택값이 된다. Jackson은 null도 키를 빼지 않고 내보내므로 모든 컴포넌트를 required로 두고,
 * {@code @Nullable} 컴포넌트에만 null 타입을 더한다. 다른 스키마를 가리키는({@code $ref}) 컴포넌트는 형제 키가 무시되므로
 * (openapi-typescript 등) {@code anyOf: [{$ref}, {type: null}]}로 감싼다.
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
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                if (property.get$ref() != null) {
                    model.getProperties().put(component.getName(), nullableRef(property));
                } else if (property.getTypes() == null || !property.getTypes().contains("null")) {
                    property.addType("null");
                }
            }
            if (model.getRequired() == null || !model.getRequired().contains(component.getName())) {
                model.addRequiredItem(component.getName());
            }
        }
        return resolved;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullableRef(Schema property) {
        Schema ref = new Schema<>();
        ref.set$ref(property.get$ref());
        Schema none = new Schema<>();
        none.addType("null");
        Schema wrapped = new Schema<>();
        wrapped.setAnyOf(List.of(ref, none));
        wrapped.setDescription(property.getDescription());
        return wrapped;
    }

    private static @Nullable Class<?> rawClass(AnnotatedType type) {
        if (type.getType() instanceof Class<?> clazz) {
            return clazz;
        }
        JavaType javaType = Json.mapper().constructType(type.getType());
        return javaType == null ? null : javaType.getRawClass();
    }
}
