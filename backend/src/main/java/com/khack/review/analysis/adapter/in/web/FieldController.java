package com.khack.review.analysis.adapter.in.web;

import com.khack.review.analysis.application.FieldTaxonomy;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학습 분야 분류표 (스펙 §7.10). 확인 단계·세션 화면의 분야 선택지에 쓴다. */
@RestController
@RequestMapping("/api/fields")
class FieldController {

    private final FieldTaxonomy taxonomy;

    FieldController(FieldTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    record FieldOption(String code, String label, List<SubfieldOption> subfields) {
    }

    record SubfieldOption(String code, String label, @Nullable String hint) {
    }

    @GetMapping
    List<FieldOption> list() {
        return taxonomy.fields().stream()
                .map(field -> new FieldOption(field.code(), field.label(), field.subfields().stream()
                        .map(subfield -> new SubfieldOption(subfield.code(), subfield.label(), subfield.hint()))
                        .toList()))
                .toList();
    }
}
