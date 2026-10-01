package com.khack.review.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.application.FieldTaxonomy.Field;
import com.khack.review.analysis.application.FieldTaxonomy.Subfield;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldTaxonomyTest {

    final FieldTaxonomy taxonomy = new FieldTaxonomy();

    @Test
    void loadsTheTaxonomyResource() {
        assertThat(taxonomy.fields()).hasSize(12);
        assertThat(taxonomy.fields()).flatExtracting(Field::subfields).hasSize(78);
        assertThat(taxonomy.subfield("cs.db")).map(Subfield::label).hasValue("데이터베이스");
        assertThat(taxonomy.fieldOf("cs.db")).map(Field::label).hasValue("컴퓨터·IT");
        assertThat(taxonomy.subfield(FieldTaxonomy.UNCLASSIFIED)).isPresent();
    }

    @Test
    void everyFieldHasItsFallback() {
        assertThat(taxonomy.fields()).allSatisfy(field ->
                assertThat(taxonomy.subfield(FieldTaxonomy.fallbackFor(field.code()))).isPresent());
    }

    @Test
    void rejectsBrokenTaxonomies() {
        assertThatThrownBy(() -> new FieldTaxonomy(List.of(new Field("etc", "기타",
                List.of(new Subfield("etc.etc", "미분류", null))), new Field("cs", "컴퓨터", List.of(new Subfield("cs.db", "DB", null))))))
                .hasMessageContaining("cs.etc");
        assertThatThrownBy(() -> new FieldTaxonomy(List.of(new Field("etc", "기타",
                List.of(new Subfield("etc.etc", "미분류", null), new Subfield("cs.db", "DB", null))))))
                .hasMessageContaining("대분류 코드로 시작");
        assertThatThrownBy(() -> new FieldTaxonomy(List.of(new Field("cs", "컴퓨터", List.of(new Subfield("cs.etc", "기타", null))))))
                .hasMessageContaining(FieldTaxonomy.UNCLASSIFIED);
    }
}
