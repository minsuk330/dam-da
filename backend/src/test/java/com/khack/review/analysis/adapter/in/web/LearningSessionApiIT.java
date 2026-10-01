package com.khack.review.analysis.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.ReviewUnitsConfirmedByUser;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.json.Json;
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

/**
 * 확인 화면 API: 조회 → 제외 → 발화 수정·추가(재검증) → 확인 완료. 검수는 테스트에서 Jev 키가 없어 UNAVAILABLE로 끝난다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(LearningSessionApiIT.Events.class)
class LearningSessionApiIT {

    @TestConfiguration
    static class Events {

        @Bean
        Confirmed confirmed() {
            return new Confirmed();
        }
    }

    static class Confirmed {

        final List<ReviewUnitsConfirmedByUser> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(ReviewUnitsConfirmedByUser event) {
            received.add(event);
        }
    }

    static final SessionInput INPUT = new SessionInput(
            List.of(new UserTurn(1, "SELECT도 락 걸어?", null, Intent.info_request, null, null),
                    new UserTurn(2, "그럼 S 락 거는 거 맞지?", null, Intent.understanding_check, AiVerdict.corrected, "일반 SELECT는 락이 없다"),
                    new UserTurn(3, "FOR UPDATE는?", null, Intent.info_request, null, null)),
            List.of(new ReviewUnit("MVCC 읽기", List.of(new KeyPoint("일반 SELECT는 스냅샷을 읽어 락을 걸지 않는다", List.of(1, 2), null)),
                            List.of(new ConfusionPoint(2, "일반 SELECT도 S 락을 건다"))),
                    new ReviewUnit("잠금 읽기", List.of(new KeyPoint("FOR UPDATE는 배타 락을 건다", List.of(3), null)), null)),
            "InnoDB");

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    LearningSessionQueryService query;

    @Autowired
    Confirmed confirmed;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        confirmed.received.clear();
    }

    @Test
    void userReviewsFixesAndConfirmsASession() throws Exception {
        long id = savedAndReviewed();

        String conversationId = conversations.findById(sessions.findById(id).orElseThrow().getConversationId()).orElseThrow()
                .getSessionId();
        JsonNode list = send("GET", "/api/learning-sessions", null, 200);
        assertThat(list.valueStream().filter(s -> s.get("id").asLong() == id).findFirst()).hasValueSatisfying(
                s -> assertThat(s.get("conversationId").asString()).as("원본 대화의 외부 ID").isEqualTo(conversationId));

        JsonNode detail = send("GET", "/api/learning-sessions/" + id, null, 200);
        assertThat(detail.get("conversationId").asString()).isEqualTo(conversationId);
        assertThat(detail.get("status").asString()).isEqualTo("AWAITING_CONFIRMATION");
        assertThat(detail.get("inputPath").asString()).isEqualTo("connector");
        assertThat(detail.get("fidelity").asString()).isEqualTo("model_transcribed");
        assertThat(detail.get("turns")).hasSize(3);
        assertThat(detail.get("units")).hasSize(2);
        assertThat(detail.get("units").get(0).get("verdict").asString()).isEqualTo("UNAVAILABLE");
        assertThat(detail.get("warnings")).isEmpty();
        long lockUnit = detail.get("units").get(1).get("id").asLong();
        long snapshotFact = detail.get("units").get(0).get("items").get(0).get("id").asLong();

        detail = send("PATCH", "/api/learning-sessions/%d/units/%d".formatted(id, lockUnit), "{\"excluded\":true}", 200);
        assertThat(detail.get("units").get(1).get("excluded").asBoolean()).isTrue();
        assertThat(detail.get("units").get(1).get("items").get(0).get("status").asString()).isEqualTo("EXCLUDED");
        assertThat(texts(detail.get("warnings"))).containsExactly("3번째 메시지: 어느 기억할 내용에도 연결되지 않았어요.");

        detail = send("PATCH", "/api/learning-sessions/%d/units/%d".formatted(id, lockUnit), "{\"excluded\":false}", 200);
        assertThat(detail.get("units").get(1).get("items").get(0).get("status").asString()).isEqualTo("NEW");
        assertThat(detail.get("warnings")).isEmpty();

        detail = send("PUT", "/api/learning-sessions/%d/turns/3".formatted(id),
                "{\"text\":\"그럼 FOR UPDATE는?\",\"intent\":\"info_request\"}", 200);
        assertThat(detail.get("turns").get(2).get("text").asString()).isEqualTo("그럼 FOR UPDATE는?");

        detail = send("POST", "/api/learning-sessions/%d/turns".formatted(id),
                "{\"afterIndex\":1,\"text\":\"스냅샷은 언제 만들어?\",\"intent\":\"info_request\"}", 201);
        assertThat(detail.get("turns").valueStream().map(t -> t.get("index").asInt()).toList()).containsExactly(1, 2, 3, 4);
        assertThat(detail.get("units").get(0).get("items").get(1).get("sourceTurns").valueStream().map(JsonNode::asInt).toList())
                .as("헷갈린 지점 출처 2번 → 3번").containsExactly(3);
        assertThat(texts(detail.get("warnings"))).containsExactly("2번째 메시지: 어느 기억할 내용에도 연결되지 않았어요.");

        detail = send("POST", "/api/learning-sessions/%d/turns".formatted(id),
                "{\"afterIndex\":4,\"text\":\"MVCC가 정확히 뭐야?\",\"intent\":\"info_request\",\"sourceOf\":[%d]}".formatted(snapshotFact), 201);
        assertThat(detail.get("units").get(0).get("evidenceTurns").valueStream().map(JsonNode::asInt).toList()).containsExactly(1, 3, 5);

        send("PUT", "/api/learning-sessions/%d/turns/9".formatted(id), "{\"text\":\"없음\",\"intent\":\"info_request\"}", 400);

        detail = send("POST", "/api/learning-sessions/%d/confirm".formatted(id), null, 200);
        assertThat(detail.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(detail.get("confirmedAt").isNull()).isFalse();
        assertThat(confirmed.received).containsExactly(new ReviewUnitsConfirmedByUser(id));
        query.requireConfirmed(id);

        send("PATCH", "/api/learning-sessions/%d/units/%d".formatted(id, lockUnit), "{\"excluded\":true}", 409);
        send("POST", "/api/learning-sessions/%d/confirm".formatted(id), null, 409);
    }

    @Test
    void questionsWaitForConfirmation() {
        long id = savedAndReviewed();

        assertThatThrownBy(() -> query.requireConfirmed(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void metaTurnCannotBecomeEvidence() throws Exception {
        long id = savedAndReviewed();
        JsonNode detail = send("GET", "/api/learning-sessions/" + id, null, 200);
        long lockFact = detail.get("units").get(1).get("items").get(0).get("id").asLong();

        send("POST", "/api/learning-sessions/%d/turns".formatted(id),
                "{\"afterIndex\":3,\"text\":\"저장해줘\",\"intent\":\"meta\",\"sourceOf\":[%d]}".formatted(lockFact), 400);

        detail = send("PUT", "/api/learning-sessions/%d/turns/3".formatted(id), "{\"text\":\"FOR UPDATE는?\",\"intent\":\"meta\"}", 200);
        JsonNode lockItem = detail.get("units").get(1).get("items").get(0);
        assertThat(lockItem.get("sourceTurns")).isEmpty();
        assertThat(lockItem.get("status").asString()).isEqualTo("EXCLUDED");
    }

    @Test
    void unknownSessionIsNotFound() throws Exception {
        send("GET", "/api/learning-sessions/999999", null, 404);
    }

    private long savedAndReviewed() {
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

    private JsonNode send(String method, String path, String body, int expectedStatus) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method + " " + path + " → " + response.body()).isEqualTo(expectedStatus);
        return Json.MAPPER.readTree(response.body());
    }

    private static List<String> texts(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
