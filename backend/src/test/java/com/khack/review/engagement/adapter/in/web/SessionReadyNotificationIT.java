package com.khack.review.engagement.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.application.UnitReviewQuestions;
import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import com.khack.review.engagement.application.port.out.FakePushNotifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;

/** 커넥터 저장 → 비동기 검수 → 확인 대기 → "학습 내용 도착" 알림 → 목록·읽음 API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.analysis.retry-delay=1ms")
@Import(SessionReadyNotificationIT.Fakes.class)
class SessionReadyNotificationIT {

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        FakeJevPort fakeJevPort() {
            return new FakeJevPort().willReturn(new JevResult("fake", Map.of(
                    UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(0.9),
                    UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9))));
        }

        @Bean
        @Primary
        FakePushNotifier fakePushNotifier() {
            return new FakePushNotifier();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    FakePushNotifier push;

    private Long saveAndAwaitReview(String topic) {
        SavedSession saved = intake.intake(new SessionInput(List.of(turn(1, "표준오차가 뭐야?")),
                List.of(new ReviewUnit("표준오차", List.of(point("표본평균의 퍼짐", 1)), null)), topic));
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            LearningSession session = sessions.findByConversationId(conversationId).orElseThrow();
            if (session.getStatus() == LearningSessionStatus.AWAITING_CONFIRMATION) {
                return session.getId();
            }
            assertThat(Instant.now()).as("검수 완료 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode notifications() throws Exception {
        return Json.MAPPER.readTree(send("GET", "/api/notifications").body());
    }

    private JsonNode notificationFor(Long sessionId) throws Exception {
        for (JsonNode item : notifications().get("items")) {
            if (item.get("targetId").asLong() == sessionId) {
                return item;
            }
        }
        throw new AssertionError("세션 %d 알림 없음".formatted(sessionId));
    }

    @Test
    void reviewedSessionCreatesAnArrivalNotificationThatCanBeRead() throws Exception {
        Long sessionId = saveAndAwaitReview("경영통계");

        JsonNode item = notificationFor(sessionId);
        assertThat(item.get("type").asString()).isEqualTo("SESSION_READY");
        assertThat(item.get("title").asString()).isEqualTo("경영통계 학습 내용이 도착했어요");
        assertThat(item.get("read").asBoolean()).isFalse();
        assertThat(push.pushes()).anyMatch(p -> p.title().equals("경영통계 학습 내용이 도착했어요"));
        long unreadBefore = notifications().get("unreadCount").asLong();

        assertThat(send("POST", "/api/notifications/" + item.get("id").asLong() + "/read").statusCode()).isEqualTo(204);

        assertThat(notificationFor(sessionId).get("read").asBoolean()).isTrue();
        assertThat(notifications().get("unreadCount").asLong()).isEqualTo(unreadBefore - 1);
    }

    @Test
    void readAllClearsTheUnreadCount() throws Exception {
        saveAndAwaitReview("InnoDB");

        assertThat(send("POST", "/api/notifications/read-all").statusCode()).isEqualTo(204);

        assertThat(notifications().get("unreadCount").asLong()).isZero();
    }

    @Test
    void unknownNotificationIsNotFound() throws Exception {
        assertThat(send("POST", "/api/notifications/999999/read").statusCode()).isEqualTo(404);
    }

    @Test
    void pushFailureStillKeepsTheInAppNotification() throws Exception {
        push.willFail(new RuntimeException("push down"));
        try {
            Long sessionId = saveAndAwaitReview("네트워크");

            assertThat(notificationFor(sessionId).get("title").asString()).isEqualTo("네트워크 학습 내용이 도착했어요");
        } finally {
            push.willFail(null);
        }
    }
}
