package com.khack.review.practice.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.json.Json;
import com.khack.review.memory.domain.MemoryStrength;
import com.khack.review.memory.domain.SessionMemorySettingsRepository;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.LearningGoal;
import com.khack.review.practice.domain.LearningGoalSet;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import tools.jackson.databind.JsonNode;

/** 저장 → 검수(테스트는 Jev 키가 없어 UNAVAILABLE) → 확인 완료 → 학습 목표 선택과 첫 학습 계획. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LearningGoalIT.Events.class)
class LearningGoalIT {

    @TestConfiguration
    static class Events {

        @Bean
        GoalSetEvents goalSetEvents() {
            return new GoalSetEvents();
        }
    }

    static class GoalSetEvents {

        final List<LearningGoalSet> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(LearningGoalSet event) {
            received.add(event);
        }
    }

    static final SessionInput INPUT = new SessionInput(
            List.of(turn(1, "표준오차가 뭐야?"),
                    new com.khack.review.collection.domain.UserTurn(2, "그럼 표본이 크면 표준편차도 줄어?", null,
                            Intent.understanding_check, AiVerdict.corrected, "표준오차가 준다")),
            List.of(new ReviewUnit("표준오차", List.of(point("표준오차는 표본평균의 퍼짐", 1),
                    new KeyPoint("표본이 커져도 표준편차는 줄지 않는다", List.of(2), FactKind.warning)),
                    List.of(new ConfusionPoint(2, "표본이 크면 표준편차가 준다")))),
            "경영통계");

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    FirstStudyPlanRepository plans;

    @Autowired
    SessionMemorySettingsRepository memorySettings;

    @Autowired
    GoalSetEvents goalSet;

    @BeforeEach
    void reset() {
        goalSet.received.clear();
    }

    private long reviewedSession() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            var session = sessions.findByConversationId(conversationId).orElseThrow();
            if (session.getStatus() == LearningSessionStatus.AWAITING_CONFIRMATION) {
                return session.getId();
            }
            assertThat(Instant.now()).as("검수 완료 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
    }

    private long confirmedSession() throws Exception {
        long id = reviewedSession();
        assertThat(send("POST", "/api/learning-sessions/%d/confirm".formatted(id), null).statusCode()).isEqualTo(200);
        return id;
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void optionsPreselectMisconceptionGoalWhenTheConversationHadConfusion() throws Exception {
        long id = confirmedSession();

        JsonNode body = Json.MAPPER.readTree(send("GET", "/api/sessions/%d/learning-goals".formatted(id), null).body());

        assertThat(body.get("available")).hasSize(7);
        assertThat(body.get("maxSelected").asInt()).isEqualTo(3);
        assertThat(body.get("saved").asBoolean()).isFalse();
        assertThat(body.get("selected").get(0).asString()).isEqualTo("CORRECT_MISCONCEPTION");
        assertThat(body.get("plan").get("questions").get(0).get("type").asString()).isEqualTo("ERROR_FINDING");
    }

    @Test
    void choosingGoalsSavesThePlanSetsStrengthAndPublishesTheEvent() throws Exception {
        long id = confirmedSession();

        HttpResponse<String> response = send("PUT", "/api/sessions/%d/learning-goals".formatted(id),
                "{\"goals\":[\"CORRECT_MISCONCEPTION\",\"CONDITION\",\"KEY_RECALL\"],\"strength\":\"UNDERSTAND\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode plan = Json.MAPPER.readTree(response.body());
        assertThat(plan.get("questions")).hasSize(3);
        assertThat(plan.get("questions").get(0).get("type").asString()).isEqualTo("ERROR_FINDING");
        assertThat(plans.findBySessionId(id)).hasValueSatisfying(saved -> {
            assertThat(saved.getGoals()).containsExactly(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.CONDITION, LearningGoal.KEY_RECALL);
            assertThat(saved.getQuestions()).hasSize(3);
        });
        assertThat(memorySettings.findById(id)).hasValueSatisfying(s -> assertThat(s.getStrength()).isEqualTo(MemoryStrength.UNDERSTAND));
        assertThat(goalSet.received).singleElement().satisfies(e -> {
            assertThat(e.sessionId()).isEqualTo(id);
            assertThat(e.plannedQuestions()).isEqualTo(3);
        });

        assertThat(send("PUT", "/api/sessions/%d/learning-goals".formatted(id), "{\"goals\":[\"PRINCIPLE\"]}").statusCode()).isEqualTo(200);
        assertThat(plans.findBySessionId(id).orElseThrow().getGoals()).containsExactly(LearningGoal.PRINCIPLE);
        JsonNode reopened = Json.MAPPER.readTree(send("GET", "/api/sessions/%d/learning-goals".formatted(id), null).body());
        assertThat(reopened.get("saved").asBoolean()).isTrue();
        assertThat(reopened.get("selected").get(0).asString()).isEqualTo("PRINCIPLE");
    }

    @Test
    void goalsCannotBeChosenBeforeConfirmation() throws Exception {
        long id = reviewedSession();

        assertThat(send("PUT", "/api/sessions/%d/learning-goals".formatted(id), "{\"goals\":[\"KEY_RECALL\"]}").statusCode())
                .isEqualTo(409);
    }

    @Test
    void invalidSelectionsAreRejected() throws Exception {
        long id = confirmedSession();
        String path = "/api/sessions/%d/learning-goals".formatted(id);

        assertThat(send("PUT", path, "{\"goals\":[]}").statusCode()).isEqualTo(400);
        assertThat(send("PUT", path, "{\"goals\":[\"KEY_RECALL\",\"KEY_RECALL\"]}").statusCode()).isEqualTo(400);
        assertThat(send("PUT", path, "{\"goals\":[\"KEY_RECALL\",\"PRINCIPLE\",\"DISTINGUISH\",\"CONDITION\"]}").statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/sessions/999999/learning-goals", null).statusCode()).isEqualTo(404);
    }
}
