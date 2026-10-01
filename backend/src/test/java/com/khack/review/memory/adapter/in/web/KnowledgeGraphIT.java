package com.khack.review.memory.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.khack.review.analysis.application.SessionFieldService;
import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.json.Json;
import com.khack.review.memory.application.MemoryStateService;
import io.github.openspacedrepetition.Rating;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** 지식 그래프: 분야 → 소분류 → 세션 → 복습 단위, 노드별 R 평균, 판정 전 세션은 미분류 (스펙 §7.10). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KnowledgeGraphIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    SessionFieldService fields;

    @Autowired
    MemoryStateService memory;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    TransactionTemplate transaction;

    record Session(Long id, List<Long> itemIds) {
    }

    private Session saveSession(String topic) {
        SavedSession saved = intake.intake(new SessionInput(
                List.of(turn(1, "표준오차가 뭐야?"), turn(2, "표준편차는?"), turn(3, "p값은?")),
                List.of(new ReviewUnit("표준오차", List.of(point("표본평균의 퍼짐", 1), point("표준편차와 다르다", 2)), null),
                        new ReviewUnit("p값", List.of(point("귀무가설 하의 확률", 3)), null)),
                topic));
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        return transaction.execute(status -> {
            LearningSession session = sessions.findByConversationId(conversationId).orElseThrow();
            return new Session(session.getId(), session.items().stream().map(MemoryItem::getId).toList());
        });
    }

    private Map<String, JsonNode> graph(JsonNode[] edges) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/knowledge-graph")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode body = Json.MAPPER.readTree(response.body());
        edges[0] = body.get("edges");
        return body.get("nodes").valueStream().collect(Collectors.toMap(node -> node.get("id").asString(), node -> node));
    }

    @Test
    void groupsSessionsByFieldWithGaugeValues() throws Exception {
        Session labeled = saveSession("경영통계");
        Session unlabeled = saveSession("통계 복습");
        fields.choose(labeled.id(), "math.stats");
        memory.review(currentUser.id(), labeled.itemIds().get(0), Rating.GOOD);
        double r = memory.retrievability(labeled.itemIds().get(0)).orElseThrow();

        JsonNode[] edges = new JsonNode[1];
        Map<String, JsonNode> nodes = graph(edges);

        JsonNode session = nodes.get("session:" + labeled.id());
        assertThat(session.get("kind").asString()).isEqualTo("SESSION");
        assertThat(session.get("label").asString()).isEqualTo("경영통계");
        assertThat(session.get("parentId").asString()).isEqualTo("subfield:math.stats");
        assertThat(session.get("sessionId").asLong()).isEqualTo(labeled.id());
        assertThat(session.get("retrievability").asDouble()).isEqualTo(r, within(1e-9));
        assertThat(session.get("checkedItems").asInt()).isEqualTo(1);
        assertThat(session.get("totalItems").asInt()).isEqualTo(3);
        assertThat(session.get("targetRetention").asDouble()).as("기억 강도를 고르기 전 기본값").isEqualTo(0.9);

        assertThat(nodes.get("subfield:math.stats").get("label").asString()).isEqualTo("확률·통계");
        assertThat(nodes.get("subfield:math.stats").get("parentId").asString()).isEqualTo("field:math");
        assertThat(nodes.get("field:math").get("label").asString()).isEqualTo("수학·통계");
        assertThat(nodes.get("field:math").get("parentId").isNull()).isTrue();

        List<JsonNode> units = nodes.values().stream()
                .filter(node -> node.get("parentId").asString("").equals("session:" + labeled.id())).toList();
        assertThat(units).extracting(node -> node.get("label").asString()).containsExactlyInAnyOrder("표준오차", "p값");
        assertThat(units).allSatisfy(node -> assertThat(node.get("kind").asString()).isEqualTo("UNIT"));

        JsonNode pending = nodes.get("session:" + unlabeled.id());
        assertThat(pending.get("parentId").asString()).as("판정 전이면 미분류").isEqualTo("subfield:etc.etc");
        assertThat(pending.get("retrievability").isNull()).isTrue();
        assertThat(nodes.get("subfield:etc.etc").get("parentId").asString()).isEqualTo("field:etc");

        assertThat(edges[0].valueStream().map(e -> e.get("source").asString() + ">" + e.get("target").asString()))
                .contains("field:math>subfield:math.stats", "subfield:math.stats>session:" + labeled.id());
    }
}
