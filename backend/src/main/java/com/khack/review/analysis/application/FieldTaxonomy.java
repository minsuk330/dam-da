package com.khack.review.analysis.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 학습 분야 분류표 (스펙 §7.10). 정본은 {@code field-taxonomy.yml}이다. 대분류마다 {@code <대분류>.etc}가 있어야 하고,
 * 전체 미분류는 {@link #UNCLASSIFIED}다. 읽을 때 구조를 검사해 잘못된 분류표로는 서버가 뜨지 않는다.
 */
@Component
public class FieldTaxonomy {

    public static final String RESOURCE = "field-taxonomy.yml";
    public static final String UNCLASSIFIED = "etc.etc";
    /** Jev choice 선택지 상한 (docs/jev.md). */
    static final int MAX_OPTIONS = 255;

    public record Field(String code, String label, List<Subfield> subfields) {
    }

    public record Subfield(String code, String label, @Nullable String hint) {
    }

    private final List<Field> fields;
    private final Map<String, Field> fieldsByCode = new LinkedHashMap<>();
    private final Map<String, Subfield> subfieldsByCode = new LinkedHashMap<>();
    private final Map<String, Field> fieldOfSubfield = new LinkedHashMap<>();

    public FieldTaxonomy() {
        this(load());
    }

    FieldTaxonomy(List<Field> fields) {
        this.fields = List.copyOf(fields);
        for (Field field : fields) {
            if (fieldsByCode.put(field.code(), field) != null) {
                throw new IllegalStateException("분류표 대분류 코드 중복: " + field.code());
            }
            if (field.subfields().size() > MAX_OPTIONS) {
                throw new IllegalStateException("분류표 %s의 소분류가 %d개를 넘습니다.".formatted(field.code(), MAX_OPTIONS));
            }
            for (Subfield subfield : field.subfields()) {
                if (!subfield.code().startsWith(field.code() + ".")) {
                    throw new IllegalStateException("소분류 코드는 대분류 코드로 시작해야 합니다: " + subfield.code());
                }
                if (subfieldsByCode.put(subfield.code(), subfield) != null) {
                    throw new IllegalStateException("분류표 소분류 코드 중복: " + subfield.code());
                }
                fieldOfSubfield.put(subfield.code(), field);
            }
            if (!subfieldsByCode.containsKey(fallbackFor(field.code()))) {
                throw new IllegalStateException("분류표 %s에 %s가 없습니다.".formatted(field.code(), fallbackFor(field.code())));
            }
        }
        if (!subfieldsByCode.containsKey(UNCLASSIFIED)) {
            throw new IllegalStateException("분류표에 미분류(%s)가 없습니다.".formatted(UNCLASSIFIED));
        }
    }

    public List<Field> fields() {
        return fields;
    }

    public Optional<Field> field(String code) {
        return Optional.ofNullable(fieldsByCode.get(code));
    }

    public Optional<Subfield> subfield(String code) {
        return Optional.ofNullable(subfieldsByCode.get(code));
    }

    /** 소분류가 속한 대분류. */
    public Optional<Field> fieldOf(String subfieldCode) {
        return Optional.ofNullable(fieldOfSubfield.get(subfieldCode));
    }

    /** 대분류 안에서 소분류를 정하지 못했을 때의 값. */
    public static String fallbackFor(String fieldCode) {
        return fieldCode + ".etc";
    }

    @SuppressWarnings("unchecked")
    static List<Field> load() {
        try (InputStream in = FieldTaxonomy.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("분류표 리소스 없음: " + RESOURCE);
            }
            Map<String, Object> root = new Yaml().load(in);
            List<Field> fields = new ArrayList<>();
            for (Map<String, Object> field : (List<Map<String, Object>>) root.get("fields")) {
                List<Subfield> subfields = new ArrayList<>();
                for (Map<String, Object> subfield : (List<Map<String, Object>>) field.get("subfields")) {
                    subfields.add(new Subfield(string(subfield, "code"), string(subfield, "label"), (String) subfield.get("hint")));
                }
                fields.add(new Field(string(field, "code"), string(field, "label"), List.copyOf(subfields)));
            }
            return fields;
        } catch (IOException e) {
            throw new IllegalStateException("분류표를 읽지 못했습니다: " + RESOURCE, e);
        }
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("분류표 항목에 %s가 없습니다: %s".formatted(key, map));
        }
        return text;
    }

    /** 코드 중복 검사를 테스트에서 쓰려고 둔다. */
    Set<String> subfieldCodes() {
        return new HashSet<>(subfieldsByCode.keySet());
    }
}
