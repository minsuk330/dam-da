package com.khack.review.memory.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.khack.review.memory.domain.MemoryStateRepository;
import io.github.openspacedrepetition.Rating;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemoryStrengthIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    MemoryStateRepository states;

    @Autowired
    MemoryStateService memory;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    TransactionTemplate transaction;

    record Session(Long id, List<Long> itemIds) {
    }

    private Session saveSession() {
        SavedSession saved = intake.intake(new SessionInput(List.of(turn(1, "표준오차가 뭐야?"), turn(2, "그럼 표준편차는?")),
                List.of(new ReviewUnit("표준오차", List.of(point("표본평균의 퍼짐", 1), point("표준편차와 다르다", 2)), null)),
                "경영통계"));
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        return transaction.execute(status -> {
            LearningSession session = sessions.findByConversationId(conversationId).orElseThrow();
            return new Session(session.getId(), session.items().stream().map(MemoryItem::getId).toList());
        });
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode options(Long sessionId) throws Exception {
        return Json.MAPPER.readTree(send("GET", "/api/sessions/" + sessionId + "/memory-strength", null).body());
    }

    @Test
    void optionsShowRetentionTypeCapAndGrowingDailyMinutes() throws Exception {
        Session session = saveSession();

        JsonNode body = options(session.id());

        assertThat(body.get("current").isNull()).isTrue();
        assertThat(body.get("itemCount").asInt()).isEqualTo(2);
        assertThat(body.get("simulationDays").asInt()).isEqualTo(30);
        JsonNode options = body.get("options");
        assertThat(options).hasSize(4);
        assertThat(options.get(0).get("strength").asString()).isEqualTo("LIGHT");
        assertThat(options.get(0).get("label").asString()).isEqualTo("가볍게 기억");
        assertThat(options.get(1).get("desiredRetention").asDouble()).isEqualTo(0.85);
        assertThat(options.get(3).get("maxQuestionLevel").asInt()).isEqualTo(3);
        assertThat(options.get(3).get("dailyMinutes").asDouble()).isGreaterThan(options.get(0).get("dailyMinutes").asDouble());
    }

    @Test
    void choosingAStrengthSetsTheRetentionOfEveryItem() throws Exception {
        Session session = saveSession();
        memory.review(currentUser.id(), session.itemIds().get(0), Rating.GOOD);

        assertThat(send("PUT", "/api/sessions/" + session.id() + "/memory-strength", "{\"strength\":\"MASTER\"}").statusCode())
                .isEqualTo(204);

        assertThat(options(session.id()).get("current").asString()).isEqualTo("MASTER");
        for (Long itemId : session.itemIds()) {
            assertThat(states.findByMemoryItemId(itemId)).hasValueSatisfying(
                    state -> assertThat(state.getDesiredRetention()).isEqualTo(0.95));
        }
        assertThat(memory.retrievability(session.itemIds().get(1))).as("아직 등급 전 항목은 확인 전").isEmpty();
        assertThat(memory.retrievability(session.itemIds().get(0))).isPresent();

        assertThat(send("PUT", "/api/sessions/" + session.id() + "/memory-strength", "{\"strength\":\"LIGHT\"}").statusCode())
                .isEqualTo(204);
        assertThat(states.findByMemoryItemId(session.itemIds().get(1)).orElseThrow().getDesiredRetention()).isEqualTo(0.80);
    }

    @Test
    void otherUsersOrUnknownSessionsAreNotFound() throws Exception {
        Long otherUsers = sessions.save(LearningSession.create(999L, 777_777L,
                new SessionInput(List.of(turn(1, "q")), List.of(new ReviewUnit("t", List.of(point("p", 1)), null)), null),
                Instant.parse("2026-10-01T00:00:00Z"))).getId();

        assertThat(send("GET", "/api/sessions/" + otherUsers + "/memory-strength", null).statusCode()).isEqualTo(404);
        assertThat(send("PUT", "/api/sessions/" + otherUsers + "/memory-strength", "{\"strength\":\"LIGHT\"}").statusCode())
                .isEqualTo(404);
        assertThat(send("GET", "/api/sessions/999999/memory-strength", null).statusCode()).isEqualTo(404);
    }

    @Test
    void missingOrUnknownStrengthIsABadRequest() throws Exception {
        Session session = saveSession();

        assertThat(send("PUT", "/api/sessions/" + session.id() + "/memory-strength", "{}").statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/sessions/" + session.id() + "/memory-strength", "{\"strength\":\"ULTRA\"}").statusCode())
                .isEqualTo(400);
    }
}
