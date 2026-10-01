package com.khack.review.collection.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.application.ConversationQueryService;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.UserTurn;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LearningSessionToolsIT {


    @Value("${local.server.port}")
    int port;

    @Autowired
    ConversationQueryService conversations;

    private SavedSession latest() {
        List<SavedSession> all = conversations.list();
        return all.get(all.size() - 1);
    }

    McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build())
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    private static Map<String, Object> validArgs() {
        return Map.of(
                "userTurns", List.of(
                        Map.of("index", 1, "text", "스캔하고 락 거는 거 아니야?", "intent", "understanding_check",
                                "aiVerdict", "corrected", "correction", "읽는 순간 잠근다"),
                        Map.of("index", 2, "text", "trx_id가 뭔데", "intent", "info_request"),
                        Map.of("index", 3, "text", "복습에 넣어줘", "intent", "meta")),
                "reviewUnits", List.of(Map.of(
                        "title", "잠금 순서",
                        "keyPoints", List.of(
                                Map.of("point", "레코드마다 즉시 잠근다", "turns", List.of(1)),
                                Map.of("point", "RC에서도 갭 락이 걸리는 경우가 있다", "turns", List.of(1), "kind", "warning"),
                                Map.of("point", "trx_id는 트랜잭션 일련번호", "turns", List.of(2))),
                        "confusionPoints", List.of(Map.of("turn", 1, "userBelief", "스캔 후 잠근다")))));
    }

    private static CallToolResult call(McpSyncClient client, Map<String, Object> args) {
        return client.callTool(new CallToolRequest("save_learning_session", args));
    }

    @Test
    void exposesOnlySaveLearningSessionAsWriteTool() {
        List<Tool> tools = client.listTools().tools();
        assertThat(tools).extracting(Tool::name).containsExactly("save_learning_session");
        Tool tool = tools.get(0);
        assertThat(tool.annotations().readOnlyHint()).isFalse();
        assertThat(tool.description()).doesNotContain("answerOpening", "answerGist", "answerKeyPoints", "evidenceTurns");
        assertThat(tool.description()).contains("오타까지 수정 없이", "understanding_check", "restatement", "추출",
                "keyPoints", "practice", "저장 기능 자체", "다르면 따로", "추가로",
                "partial·corrected인 발화는 모두 confusionPoints에");
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaMarksOnlyEssentialFieldsRequired() {
        Map<String, Object> schema = client.listTools().tools().get(0).inputSchema();
        assertThat((List<String>) schema.get("required")).containsExactlyInAnyOrder("userTurns", "reviewUnits");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsOnlyKeys("userTurns", "reviewUnits", "topicHint");

        Map<String, Object> turnItem = (Map<String, Object>) ((Map<String, Object>) properties.get("userTurns")).get("items");
        assertThat((Map<String, Object>) turnItem.get("properties"))
                .containsOnlyKeys("index", "text", "quotedText", "intent", "aiVerdict", "correction");
        assertThat((List<String>) turnItem.get("required")).containsExactlyInAnyOrder("index", "text", "intent");

        Map<String, Object> unitItem = (Map<String, Object>) ((Map<String, Object>) properties.get("reviewUnits")).get("items");
        Map<String, Object> unitProps = (Map<String, Object>) unitItem.get("properties");
        assertThat(unitProps).containsOnlyKeys("title", "keyPoints", "confusionPoints");
        assertThat((List<String>) unitItem.get("required")).containsExactlyInAnyOrder("title", "keyPoints");

        Map<String, Object> pointItem = (Map<String, Object>) ((Map<String, Object>) unitProps.get("keyPoints")).get("items");
        assertThat((List<String>) pointItem.get("required")).containsExactlyInAnyOrder("point", "turns");
        assertThat((List<String>) ((Map<String, Object>) ((Map<String, Object>) pointItem.get("properties")).get("kind")).get("enum"))
                .containsExactlyInAnyOrder("fact", "warning", "practice");
    }

    @Test
    void storesMinimalValidSessionAndAnswersWithId() {
        CallToolResult result = call(client, validArgs());

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        SavedSession saved = latest();
        assertThat(saved.userTurns()).extracting(UserTurn::intent)
                .containsExactly(Intent.understanding_check, Intent.info_request, Intent.meta);
        assertThat(saved.userTurns().get(1).aiVerdict()).isNull();
        assertThat(saved.reviewUnits().get(0).keyPoints().get(1).kind()).isEqualTo(FactKind.warning);
        assertThat(saved.reviewUnits().get(0).evidenceTurns()).containsExactly(1, 2);
        assertThat(saved.warnings()).isEmpty();
        assertThat(text(result)).contains(saved.id());
    }

    @Test
    void rejectsStructuralErrorsWithFixHintsAndSavesNothing() {
        int before = conversations.list().size();
        Map<String, Object> args = new java.util.HashMap<>(validArgs());
        args.put("reviewUnits", List.of(Map.of("title", "t", "keyPoints", List.of(Map.of("point", "p", "turns", List.of(9))))));

        CallToolResult result = call(client, args);

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("reviewUnits[0].keyPoints[0].turns", "9");
        assertThat(conversations.list()).hasSize(before);
    }

    @Test
    void rejectsUnknownIntentValue() {
        int before = conversations.list().size();
        Map<String, Object> args = new java.util.HashMap<>(validArgs());
        args.put("userTurns", List.of(Map.of("index", 1, "text", "q", "intent", "curiosity")));
        boolean rejected;
        try {
            rejected = Boolean.TRUE.equals(call(client, args).isError());
        } catch (RuntimeException e) {
            rejected = true;
        }
        assertThat(rejected).isTrue();
        assertThat(conversations.list()).hasSize(before);
    }

    @Test
    void savesUncoveredLearningTurnWithWarning() {
        Map<String, Object> args = new java.util.HashMap<>(validArgs());
        args.put("reviewUnits", List.of(Map.of("title", "t",
                "keyPoints", List.of(Map.of("point", "레코드마다 즉시 잠근다", "turns", List.of(1))),
                "confusionPoints", List.of(Map.of("turn", 1, "userBelief", "스캔 후 잠근다")))));

        CallToolResult result = call(client, args);

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(latest().warnings()).anyMatch(w -> w.startsWith("userTurns[1]") && w.contains("반영"));
        assertThat(text(result)).contains("경고 1건");
    }

    @Test
    void healthEndpointAnswersOk() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/healthz")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"ok\":true");
    }
}
