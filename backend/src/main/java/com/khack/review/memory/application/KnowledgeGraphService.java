package com.khack.review.memory.application;

import com.khack.review.analysis.application.FieldLabel;
import com.khack.review.analysis.application.FieldLabels;
import com.khack.review.analysis.application.FieldTaxonomy;
import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.analysis.application.SessionItemsQuery.GraphSession;
import com.khack.review.analysis.application.SessionItemsQuery.UnitItems;
import com.khack.review.memory.application.MemoryGaugeService.ItemGauge;
import com.khack.review.memory.domain.MemoryStrength;
import com.khack.review.memory.domain.SessionMemorySettings;
import com.khack.review.memory.domain.SessionMemorySettingsRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지식 그래프 (스펙 §7.10). 대분류 → 소분류 → 세션 → 복습 단위의 4단 트리를 노드 목록으로 돌려준다.
 * 노드의 R은 그 아래 확인된 기억 항목 R의 평균(기억 게이지 §6.4.3과 같은 계산)이고, 목표 유지율은 소속 세션 목표의 평균이다.
 * 색 단계는 화면이 R과 목표 유지율로 정한다. 분야 판정 전인 세션은 미분류 아래에 둔다.
 */
@Service
public class KnowledgeGraphService {

    /** 기억 강도를 고르기 전 세션의 목표 유지율. 화면의 기본값(DEFAULT_TARGET_RETENTION)과 같다. */
    static final double DEFAULT_TARGET_RETENTION = MemoryStrength.APPLY.desiredRetention();

    public enum NodeKind {
        FIELD, SUBFIELD, SESSION, UNIT
    }

    /**
     * @param id              그래프 안에서 고유한 ID ({@code field:cs}, {@code subfield:cs.db}, {@code session:12}, {@code unit:34})
     * @param parentId        상위 노드. 대분류는 null
     * @param sessionId       세션·복습 단위 노드가 속한 세션
     * @param retrievability  확인된 기억 항목 R의 평균. 확인된 항목이 없으면 null(회색)
     * @param targetRetention 소속 세션 목표 유지율의 평균
     */
    public record Node(String id, NodeKind kind, String label, @Nullable String parentId, @Nullable Long sessionId,
            @Nullable Double retrievability, double targetRetention, int checkedItems, int totalItems) {
    }

    /** 상위 → 하위 소속 관계. */
    public record Edge(String source, String target) {
    }

    public record KnowledgeGraph(List<Node> nodes, List<Edge> edges) {
    }

    private final SessionItemsQuery sessionItems;
    private final FieldLabels fieldLabels;
    private final FieldTaxonomy taxonomy;
    private final MemoryGaugeService gauges;
    private final SessionMemorySettingsRepository settings;

    public KnowledgeGraphService(SessionItemsQuery sessionItems, FieldLabels fieldLabels, FieldTaxonomy taxonomy,
            MemoryGaugeService gauges, SessionMemorySettingsRepository settings) {
        this.sessionItems = sessionItems;
        this.fieldLabels = fieldLabels;
        this.taxonomy = taxonomy;
        this.gauges = gauges;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public KnowledgeGraph graph(Long userId) {
        List<GraphSession> sessions = sessionItems.graphSessionsOf(userId);
        Map<Long, FieldLabel> labels = fieldLabels.of(sessions.stream().map(GraphSession::sessionId).toList());
        Map<Long, Double> targets = settings.findByUserId(userId).stream().collect(Collectors.toMap(
                SessionMemorySettings::getSessionId, s -> s.getStrength().desiredRetention(), (first, later) -> first));
        Map<Long, ItemGauge> itemGauges = gauges.itemGauges(sessions.stream()
                .flatMap(session -> session.units().stream()).flatMap(unit -> unit.items().stream())
                .map(SessionItemsQuery.Item::memoryItemId).toList());

        Map<String, List<GraphSession>> bySubfield = new LinkedHashMap<>();
        for (GraphSession session : sessions) {
            FieldLabel label = labels.get(session.sessionId());
            bySubfield.computeIfAbsent(label == null ? FieldTaxonomy.UNCLASSIFIED : label.code(), code -> new ArrayList<>()).add(session);
        }

        List<Node> nodes = new ArrayList<>();
        Map<String, List<String>> subfieldsByField = new LinkedHashMap<>();
        bySubfield.keySet().stream().sorted(Comparator.comparingInt(this::taxonomyOrder))
                .forEach(code -> subfieldsByField.computeIfAbsent(fieldCodeOf(code), field -> new ArrayList<>()).add(code));

        for (Map.Entry<String, List<String>> field : subfieldsByField.entrySet()) {
            String fieldId = "field:" + field.getKey();
            List<GraphSession> fieldSessions = field.getValue().stream().flatMap(code -> bySubfield.get(code).stream()).toList();
            nodes.add(aggregate(fieldId, NodeKind.FIELD, fieldLabel(field.getKey()), null, null, fieldSessions, itemGauges, targets));
            for (String subfield : field.getValue()) {
                String subfieldId = "subfield:" + subfield;
                List<GraphSession> subSessions = bySubfield.get(subfield);
                nodes.add(aggregate(subfieldId, NodeKind.SUBFIELD, subfieldLabel(subfield), fieldId, null, subSessions, itemGauges, targets));
                for (GraphSession session : subSessions) {
                    String sessionNodeId = "session:" + session.sessionId();
                    nodes.add(aggregate(sessionNodeId, NodeKind.SESSION, session.title(), subfieldId, session.sessionId(),
                            List.of(session), itemGauges, targets));
                    for (UnitItems unit : session.units()) {
                        nodes.add(node("unit:" + unit.unitId(), NodeKind.UNIT, unit.title(), sessionNodeId, session.sessionId(),
                                gaugesOf(List.of(unit), itemGauges), targetOf(session, targets)));
                    }
                }
            }
        }
        List<Edge> edges = nodes.stream().filter(node -> node.parentId() != null)
                .map(node -> new Edge(node.parentId(), node.id())).toList();
        return new KnowledgeGraph(nodes, edges);
    }

    private Node aggregate(String id, NodeKind kind, String label, @Nullable String parentId, @Nullable Long sessionId,
            List<GraphSession> sessions, Map<Long, ItemGauge> itemGauges, Map<Long, Double> targets) {
        List<ItemGauge> items = sessions.stream().flatMap(session -> gaugesOf(session.units(), itemGauges).stream()).toList();
        double target = sessions.stream().mapToDouble(session -> targetOf(session, targets)).average().orElse(DEFAULT_TARGET_RETENTION);
        return node(id, kind, label, parentId, sessionId, items, target);
    }

    private static Node node(String id, NodeKind kind, String label, @Nullable String parentId, @Nullable Long sessionId,
            List<ItemGauge> items, double target) {
        MemoryGaugeService.UnitGauge gauge = MemoryGaugeService.unitGauge(items);
        return new Node(id, kind, label, parentId, sessionId, gauge.average(), target, gauge.checkedItems(), gauge.totalItems());
    }

    private static List<ItemGauge> gaugesOf(List<UnitItems> units, Map<Long, ItemGauge> itemGauges) {
        return units.stream().flatMap(unit -> unit.items().stream())
                .map(item -> itemGauges.get(item.memoryItemId())).toList();
    }

    private static double targetOf(GraphSession session, Map<Long, Double> targets) {
        return targets.getOrDefault(session.sessionId(), DEFAULT_TARGET_RETENTION);
    }

    private int taxonomyOrder(String subfieldCode) {
        int index = 0;
        for (FieldTaxonomy.Field field : taxonomy.fields()) {
            for (FieldTaxonomy.Subfield subfield : field.subfields()) {
                if (subfield.code().equals(subfieldCode)) {
                    return index;
                }
                index++;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String fieldCodeOf(String subfieldCode) {
        return taxonomy.fieldOf(subfieldCode).map(FieldTaxonomy.Field::code)
                .orElse(subfieldCode.contains(".") ? subfieldCode.substring(0, subfieldCode.indexOf('.')) : subfieldCode);
    }

    private String fieldLabel(String fieldCode) {
        return taxonomy.field(fieldCode).map(FieldTaxonomy.Field::label).orElse(fieldCode);
    }

    private String subfieldLabel(String subfieldCode) {
        return taxonomy.subfield(subfieldCode).map(FieldTaxonomy.Subfield::label).orElse(subfieldCode);
    }
}
