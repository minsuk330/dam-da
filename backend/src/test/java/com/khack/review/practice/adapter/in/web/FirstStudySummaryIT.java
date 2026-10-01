package com.khack.review.practice.adapter.in.web;

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
import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.common.json.Json;
import com.khack.review.memory.application.ReviewRecordService;
import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.ReviewContext;
import com.khack.review.memory.domain.SelfAssessment;
import com.khack.review.question.domain.QuestionType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** 첫 학습 완료 요약: 확인한 항목·도움이 필요했던 항목·확인 전 항목·다음 복습 일정·시간 이동 뒤 게이지. 판정은 가짜 값으로 넣는다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FirstStudySummaryIT {

    static final AtomicLong IDS = new AtomicLong(700_000);

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    ReviewRecordService reviews;

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

    private Session saveSession() {
        SavedSession saved = intake.intake(new SessionInput(
                List.of(turn(1, "표준오차가 뭐야?"), turn(2, "표준편차는?"), turn(3, "신뢰구간은?")),
                List.of(new ReviewUnit("표준오차", List.of(point("표본평균의 퍼짐", 1), point("표준편차와 다르다", 2), point("신뢰구간은 추정 범위", 3)), null)),
                "경영통계"));
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        return transaction.execute(status -> {
            LearningSession session = sessions.findByConversationId(conversationId).orElseThrow();
            return new Session(session.getId(), session.items().stream().map(MemoryItem::getId).toList());
        });
    }

    private void answer(Long item, AnswerVerdict verdict, boolean aid) {
        reviews.record(new ReviewContext(currentUser.id(), item, 70L, IDS.incrementAndGet(), clock.instant(), null, aid, null,
                false, List.of(), null),
                new RatingInput(AttemptKind.FIRST_UNASSISTED, verdict, 0.9, false, 0, false,
                        SelfAssessment.RECALLED_WITH_EFFORT, QuestionType.SHORT_ANSWER, Duration.ofSeconds(30)));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode summary(Long sessionId) throws Exception {
        HttpResponse<String> response = get("/api/sessions/" + sessionId + "/first-study/summary");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    @Test
    void summarySplitsConfirmedHelpedAndNotCheckedItems() throws Exception {
        Session session = saveSession();
        Long confirmed = session.itemIds().get(0);
        Long helped = session.itemIds().get(1);
        Long untouched = session.itemIds().get(2);
        answer(confirmed, AnswerVerdict.MET, false);
        answer(helped, AnswerVerdict.NOT_MET, false);
        clock.travel(Duration.ofDays(1));

        JsonNode summary = summary(session.id());

        assertThat(summary.get("completed").asBoolean()).as("풀이 세션이 없으면 완료 아님").isFalse();
        assertThat(summary.get("confirmed")).extracting(n -> n.get("memoryItemId").asLong()).containsExactly(confirmed);
        assertThat(summary.get("needsHelp")).extracting(n -> n.get("memoryItemId").asLong()).containsExactly(helped);
        assertThat(summary.get("notChecked")).extracting(n -> n.get("memoryItemId").asLong()).containsExactly(untouched);
        JsonNode confirmedItem = summary.get("confirmed").get(0);
        assertThat(confirmedItem.get("content").asString()).isEqualTo("표본평균의 퍼짐");
        assertThat(confirmedItem.get("gauge").get("percent").asInt()).isGreaterThan(80);
        assertThat(confirmedItem.get("nextReviewAt").isNull()).isFalse();
        assertThat(summary.get("notChecked").get(0).get("gauge").get("checked").asBoolean()).isFalse();
        assertThat(summary.get("notChecked").get(0).get("nextReviewAt").isNull()).isTrue();
        Instant earliest = Instant.parse(summary.get("nextReviewAt").asString());
        assertThat(earliest).as("다음 복습 일정은 항목들의 가장 이른 시각").isEqualTo(java.util.stream.Stream.of(confirmedItem, summary.get("needsHelp").get(0))
                .map(n -> Instant.parse(n.get("nextReviewAt").asString())).min(Instant::compareTo).orElseThrow());
        assertThat(earliest).isAfter(Instant.parse("2026-10-01T00:00:00Z"));
        JsonNode unit = summary.get("units").get(0);
        assertThat(unit.get("gauge").get("weakestItemId").asLong()).isEqualTo(helped);
        assertThat(unit.get("gauge").get("checkedItems").asInt()).isEqualTo(2);
    }

    @Test
    void aidExposedAttemptCountsAsNeedingHelpEvenWhenAnsweredRight() throws Exception {
        Session session = saveSession();
        answer(session.itemIds().get(0), AnswerVerdict.MET, true);

        JsonNode summary = summary(session.id());

        assertThat(summary.get("confirmed")).isEmpty();
        assertThat(summary.get("needsHelp")).extracting(n -> n.get("memoryItemId").asLong()).containsExactly(session.itemIds().get(0));
    }

    @Test
    void summaryGaugeFallsAfterTimeTravel() throws Exception {
        Session session = saveSession();
        answer(session.itemIds().get(0), AnswerVerdict.MET, false);
        int before = summary(session.id()).get("confirmed").get(0).get("gauge").get("percent").asInt();

        clock.travel(Duration.ofDays(20));
        JsonNode later = summary(session.id());

        assertThat(later.get("confirmed").get(0).get("gauge").get("percent").asInt()).isLessThan(before);
        assertThat(later.get("units").get(0).get("gauge").get("averagePercent").asInt()).isLessThan(before);
    }

    @Test
    void unknownSessionIsNotFound() throws Exception {
        assertThat(get("/api/sessions/999999/first-study/summary").statusCode()).isEqualTo(404);
    }
}
