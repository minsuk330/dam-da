package com.khack.review.memory.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.common.json.Json;
import com.khack.review.memory.application.MemoryStateService;
import io.github.openspacedrepetition.Rating;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** 기억 게이지: 항목별 R, 아직 확인 전 항목, 단위 평균·가장 약한 항목, 시간 이동 시 하락과 복습 뒤 회복 (스펙 §6.4.3). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemoryGaugeIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    MemoryStateService memory;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    TimeTravelClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    record Session(Long id, List<Long> itemIds) {
    }

    /** 단위 1(항목 3개), 단위 2(항목 1개). */
    private Session saveSession() {
        SavedSession saved = intake.intake(new SessionInput(
                List.of(turn(1, "표준오차가 뭐야?"), turn(2, "표준편차는?"), turn(3, "신뢰구간은?"), turn(4, "p값은?")),
                List.of(new ReviewUnit("표준오차", List.of(point("표본평균의 퍼짐", 1), point("표준편차와 다르다", 2), point("표본이 크면 준다", 3)), null),
                        new ReviewUnit("p값", List.of(point("귀무가설 하의 확률", 4)), null)),
                "경영통계"));
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        return transaction.execute(status -> {
            LearningSession session = sessions.findByConversationId(conversationId).orElseThrow();
            return new Session(session.getId(), session.items().stream().map(MemoryItem::getId).toList());
        });
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode gauge(Long sessionId) throws Exception {
        HttpResponse<String> response = get("/api/sessions/" + sessionId + "/memory-gauge");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    @Test
    void itemsWithoutARatingAreNotCheckedYet() throws Exception {
        Session session = saveSession();

        JsonNode units = gauge(session.id()).get("units");

        assertThat(units).hasSize(2);
        JsonNode first = units.get(0);
        assertThat(first.get("title").asString()).isEqualTo("표준오차");
        assertThat(first.get("items")).hasSize(3);
        assertThat(first.get("items").get(0).get("gauge").get("checked").asBoolean()).isFalse();
        assertThat(first.get("items").get(0).get("gauge").get("retrievability").isNull()).isTrue();
        assertThat(first.get("items").get(0).get("gauge").get("percent").isNull()).isTrue();
        assertThat(first.get("gauge").get("average").isNull()).isTrue();
        assertThat(first.get("gauge").get("weakestItemId").isNull()).isTrue();
        assertThat(first.get("gauge").get("checkedItems").asInt()).isZero();
        assertThat(first.get("gauge").get("totalItems").asInt()).isEqualTo(3);
    }

    @Test
    void unitGaugeAveragesCheckedItemsAndPointsAtTheWeakest() throws Exception {
        Session session = saveSession();
        Long strong = session.itemIds().get(0);
        Long weak = session.itemIds().get(1);
        memory.review(currentUser.id(), strong, Rating.EASY);
        memory.review(currentUser.id(), weak, Rating.AGAIN);
        clock.travel(Duration.ofDays(2));
        double strongR = memory.retrievability(strong).orElseThrow();
        double weakR = memory.retrievability(weak).orElseThrow();

        JsonNode unit = gauge(session.id()).get("units").get(0);

        assertThat(weakR).isLessThan(strongR);
        assertThat(unit.get("gauge").get("average").asDouble()).isEqualTo((strongR + weakR) / 2, within(1e-9));
        assertThat(unit.get("gauge").get("averagePercent").asInt()).isEqualTo((int) Math.round((strongR + weakR) / 2 * 100));
        assertThat(unit.get("gauge").get("weakestItemId").asLong()).isEqualTo(weak);
        assertThat(unit.get("gauge").get("weakestPercent").asInt()).isEqualTo((int) Math.round(weakR * 100));
        assertThat(unit.get("gauge").get("checkedItems").asInt()).as("확인 전 항목은 평균에서 뺀다").isEqualTo(2);
        assertThat(unit.get("gauge").get("totalItems").asInt()).isEqualTo(3);
        assertThat(unit.get("items").get(2).get("gauge").get("checked").asBoolean()).isFalse();
    }

    @Test
    void gaugeDropsAfterTimeTravelAndRecoversAfterReview() throws Exception {
        Session session = saveSession();
        Long item = session.itemIds().get(0);
        memory.review(currentUser.id(), item, Rating.GOOD);
        int before = gauge(session.id()).get("units").get(0).get("items").get(0).get("gauge").get("percent").asInt();
        assertThat(before).isGreaterThan(90);

        clock.travel(Duration.ofDays(30));
        JsonNode later = gauge(session.id()).get("units").get(0);
        int dropped = later.get("items").get(0).get("gauge").get("percent").asInt();
        assertThat(dropped).isLessThan(before);
        assertThat(later.get("gauge").get("averagePercent").asInt()).isEqualTo(dropped);

        memory.review(currentUser.id(), item, Rating.GOOD);
        int recovered = gauge(session.id()).get("units").get(0).get("items").get(0).get("gauge").get("percent").asInt();
        assertThat(recovered).isGreaterThan(dropped);
    }

    @Test
    void otherUsersOrUnknownSessionsAreNotFound() throws Exception {
        Long otherUsers = sessions.save(LearningSession.create(999L, 777_778L,
                new SessionInput(List.of(turn(1, "q")), List.of(new ReviewUnit("t", List.of(point("p", 1)), null)), null),
                Instant.parse("2026-10-01T00:00:00Z"))).getId();

        assertThat(get("/api/sessions/" + otherUsers + "/memory-gauge").statusCode()).isEqualTo(404);
        assertThat(get("/api/sessions/999999/memory-gauge").statusCode()).isEqualTo(404);
    }
}
