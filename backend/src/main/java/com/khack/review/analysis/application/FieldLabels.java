package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.SessionField;
import com.khack.review.analysis.domain.SessionFieldRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 세션의 분야 라벨을 분류표 이름과 함께 읽는다 (스펙 §7.10). 아직 판정 전인 세션은 라벨이 없다. */
@Component
public class FieldLabels {

    private final SessionFieldRepository fields;
    private final FieldTaxonomy taxonomy;

    public FieldLabels(SessionFieldRepository fields, FieldTaxonomy taxonomy) {
        this.fields = fields;
        this.taxonomy = taxonomy;
    }

    /** 세션 ID → 라벨. */
    @Transactional(readOnly = true)
    public Map<Long, FieldLabel> of(Collection<Long> sessionIds) {
        return fields.findAllBySessionIdIn(sessionIds).stream()
                .collect(Collectors.toMap(SessionField::getSessionId, this::label));
    }

    @Transactional(readOnly = true)
    public Optional<FieldLabel> of(Long sessionId) {
        return fields.findById(sessionId).map(this::label);
    }

    private FieldLabel label(SessionField field) {
        String code = field.getCode();
        String fieldCode = taxonomy.fieldOf(code).map(FieldTaxonomy.Field::code)
                .orElse(code.contains(".") ? code.substring(0, code.indexOf('.')) : code);
        return new FieldLabel(code,
                taxonomy.subfield(code).map(FieldTaxonomy.Subfield::label).orElse(code),
                fieldCode,
                taxonomy.field(fieldCode).map(FieldTaxonomy.Field::label).orElse(fieldCode),
                field.getSource());
    }
}
