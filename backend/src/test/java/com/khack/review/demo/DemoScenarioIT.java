package com.khack.review.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.application.UnitReviewQuestions;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import com.khack.review.engagement.application.DailyReminderService;
import com.khack.review.practice.application.AnswerJudgeFixtures;
import com.khack.review.practice.application.AnswerJudgeQuestions;
import com.khack.review.question.application.QuestionQualityQuestions;
import com.khack.review.question.application.port.out.FakeQuestionGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;

/**
 * 데모 시나리오(스펙 §11.3, 도메인 스토리 S1·S2)를 앱이 부르는 API 순서대로 한 번에 확인한다.
 * 대화 저장 → 학습 내용 도착 알림 → 확인 → 학습 목표 → 첫 학습 → 완료 요약 → 시간 이동 → 게이지 하락 →
 * 매일 학습 알림 → 매일 학습 → 게이지 회복 → 연속 학습 일수. LLM·Jev는 가짜이고, 답은 모두 맞게 낸다.
 * 이 클래스만 쓰는 설정이라 컨텍스트와 H2 DB가 따로 만들어져 다른 테스트의 데이터와 섞이지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DemoScenarioIT.Fakes.class)
class DemoScenarioIT {

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        JevPort passingJev() {
            return new PassingJev();
        }

        @Bean
        FakeQuestionGenerator fakeQuestionGenerator() {
            return new FakeQuestionGenerator();
        }
    }

    /** 검수·품질 검사 통과, 답변은 모두 맞음. 다음 행동 선택은 실패시켜 규칙의 기본 행동을 쓰게 한다. */
    static class PassingJev implements JevPort {

        @Override
        public JevResult evaluate(Object state, Map<String, JevQuestion> questions) {
            if (questions.containsKey(AnswerJudgeQuestions.VERDICT)) {
                return new JevResult("fake-judge", Map.of(
                        AnswerJudgeQuestions.VERDICT, new JevAnswer.Choice("met", Map.of("met", 0.95), 0.95),
                        AnswerJudgeQuestions.OMISSION, new JevAnswer.Noul(0.05),
                        AnswerJudgeQuestions.CONTRADICTION, new JevAnswer.Noul(0.05),
                        AnswerJudgeQuestions.MISREAD, AnswerJudgeFixtures.misread(0.05),
                        AnswerJudgeQuestions.REPEATS_USER_BELIEF, new JevAnswer.Noul(0.05),
                        AnswerJudgeQuestions.OFF_TARGET_ERROR, new JevAnswer.Noul(0.05)));
            }
            if (questions.containsKey(QuestionQualityQuestions.GROUNDED)) {
                return new JevResult("fake", Map.of(
                        QuestionQualityQuestions.GROUNDED, new JevAnswer.Noul(0.95),
                        QuestionQualityQuestions.CLARITY, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9),
                        QuestionQualityQuestions.DUPLICATE, new JevAnswer.Noul(0.05)));
            }
            if (questions.containsKey(UnitReviewQuestions.WORTH_REVIEWING)) {
                return new JevResult("fake", Map.of(
                        UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(0.95),
                        UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9)));
            }
            throw new JevCallException(400, "데모 시나리오에서는 쓰지 않는 질문", null);
        }
    }

    /** 데모 대화: InnoDB 잠금. 1 질문, 2 교정받음(헷갈린 지점), 3 확인 질문이 맞음, 4 실무 질문, 5 저장 요청. */
    static final SessionInput CONVERSATION = new SessionInput(
            List.of(new UserTurn(1, "InnoDB에서 SELECT 하면 락이 걸려?", null, Intent.info_request, null, null),
                    new UserTurn(2, "그럼 SELECT도 S 락 거는 거 맞지?", null, Intent.understanding_check, AiVerdict.corrected,
                            "일반 SELECT는 락 없이 스냅샷을 읽는다. FOR SHARE를 붙여야 S 락을 건다"),
                    new UserTurn(3, "그러니까 MVCC 스냅샷을 읽는 거지?", null, Intent.understanding_check, AiVerdict.confirmed, null),
                    new UserTurn(4, "락이 걸렸는지 어떻게 확인해?", null, Intent.info_request, null, null),
                    new UserTurn(5, "이거 학습에 넘겨줘", null, Intent.meta, null, null)),
            List.of(new ReviewUnit("MVCC와 일반 SELECT",
                            List.of(new KeyPoint("일반 SELECT는 MVCC 스냅샷을 읽어 행 락을 걸지 않는다", List.of(1, 3), null),
                                    new KeyPoint("FOR SHARE를 붙여야 S 락을 건다", List.of(2), FactKind.warning)),
                            List.of(new ConfusionPoint(2, "일반 SELECT도 S 락을 건다"))),
                    new ReviewUnit("잠금 확인",
                            List.of(new KeyPoint("performance_schema.data_locks로 잡힌 락을 확인한다", List.of(4), FactKind.practice)),
                            null)),
            "InnoDB 잠금");

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService connector;

    @Autowired
    DailyReminderService reminders;

    @Autowired
    TimeTravelClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void conversationToDailyReviewAndStreak() throws Exception {
        // 1. Claude 커넥터가 대화를 저장한다(save_learning_session과 같은 경로)
        connector.intake(CONVERSATION);
        long sessionId = await(() -> {
            JsonNode sessions = get("/api/learning-sessions");
            return sessions.isEmpty() || !"AWAITING_CONFIRMATION".equals(sessions.get(0).get("status").asString())
                    ? null : sessions.get(0).get("id").asLong();
        }, "검수 끝나 확인 대기");

        // 2. 학습 내용 도착 알림
        assertThat(get("/api/notifications").get("items").valueStream().map(n -> n.get("type").asString()))
                .contains("SESSION_READY");

        // 3. 확인: 발화와 복습 단위를 보고 확인을 마친다 → 대화 신호로 초기 평가
        JsonNode detail = get("/api/learning-sessions/" + sessionId);
        assertThat(detail.get("turns")).hasSize(5);
        assertThat(detail.get("units")).hasSize(2);
        assertThat(send("POST", "/api/learning-sessions/%d/confirm".formatted(sessionId), null).get("status").asString())
                .isEqualTo("CONFIRMED");
        JsonNode seededGauge = get("/api/sessions/%d/memory-gauge".formatted(sessionId));
        assertThat(checkedItems(seededGauge)).as("헷갈린 지점(Again)·확인된 이해(Good)는 확인 직후부터 게이지가 있다").isPositive();

        // 4. 학습 목표 → 문제 생성·품질 검사 → 문제 준비
        send("PUT", "/api/sessions/%d/learning-goals".formatted(sessionId),
                "{\"goals\":[\"CORRECT_MISCONCEPTION\",\"KEY_RECALL\",\"CONDITION\"]}");
        await(() -> "QUESTIONS_READY".equals(get("/api/learning-sessions/" + sessionId).get("status").asString()) ? true : null,
                "첫 학습 문제 준비");

        // 5. 첫 학습: 모든 문제를 혼자 맞힌다
        long firstStudy = send("POST", "/api/sessions/%d/first-study/practice".formatted(sessionId), null).get("practiceId").asLong();
        List<String> firstRatings = solveAll(firstStudy);
        assertThat(firstRatings).isNotEmpty().doesNotContain("AGAIN");

        // 6. 첫 학습 완료 요약
        JsonNode summary = get("/api/sessions/%d/first-study/summary".formatted(sessionId));
        assertThat(summary.get("completed").asBoolean()).isTrue();
        assertThat(summary.get("confirmed")).isNotEmpty();
        assertThat(summary.get("nextReviewAt").isNull()).isFalse();
        Map<Long, Integer> before = percents(get("/api/sessions/%d/memory-gauge".formatted(sessionId)));

        // 7. 시간 이동 30일 → 게이지 하락
        assertThat(postStatus("/dev/clock/travel?days=30")).isEqualTo(200);
        Map<Long, Integer> later = percents(get("/api/sessions/%d/memory-gauge".formatted(sessionId)));
        assertThat(later).as("확인한 항목은 모두 기억할 확률이 내려간다").allSatisfy((item, percent) ->
                assertThat(percent).isLessThan(before.get(item)));

        // 8. 매일 학습 알림 시각이 지나면 "오늘의 학습 · 약 N분"
        LocalTime now = clock.instant().atZone(clock.getZone()).toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        send("PUT", "/api/settings/daily", "{\"budgetMinutes\":5,\"notifyAt\":\"%s\"}".formatted(now));
        assertThat(reminders.sendDue()).singleElement().satisfies(n -> assertThat(n.getTitle()).startsWith("오늘의 학습 · 약 "));

        // 9. 매일 학습: 오늘의 큐를 보고 시작해 다 푼다
        JsonNode today = get("/api/daily");
        assertThat(today.get("total").asInt()).isPositive();
        assertThat(today.get("estimatedSeconds").asLong()).isLessThanOrEqualTo(Duration.ofMinutes(5).toSeconds() + 120);
        JsonNode started = send("POST", "/api/daily/start", null);
        assertThat(started.get("started").asBoolean()).isTrue();
        List<String> dailyRatings = solveAll(started.get("practiceId").asLong());
        assertThat(dailyRatings).isNotEmpty();
        assertThat(get("/api/daily").get("completed").asBoolean()).isTrue();

        // 10. 복습한 항목의 게이지가 회복된다
        Map<Long, Integer> recovered = percents(get("/api/sessions/%d/memory-gauge".formatted(sessionId)));
        assertThat(recovered.entrySet()).as("매일 학습에서 푼 항목은 다시 올라간다")
                .anySatisfy(entry -> assertThat(entry.getValue()).isGreaterThan(later.getOrDefault(entry.getKey(), 0)));

        // 11. 연속 학습 일수
        JsonNode streak = get("/api/streak");
        assertThat(streak.get("current").asInt()).isEqualTo(1);
        assertThat(streak.get("today").asString()).isEqualTo("COMPLETED");
    }

    /** 풀이의 문제를 끝까지 맞게 푼다. 객관식은 0번(가짜 생성기의 정답), 그 밖에는 가짜 Jev가 맞다고 판정한다. 받은 등급을 돌려준다. */
    private List<String> solveAll(long practiceId) throws Exception {
        List<String> ratings = new ArrayList<>();
        for (int guard = 0; guard < 30; guard++) {
            JsonNode next = get("/api/practice/%d/next".formatted(practiceId));
            if (next.get("done").asBoolean()) {
                return ratings;
            }
            JsonNode presentation = next.get("presentation");
            clock.travel(Duration.ofSeconds(15));
            String body = "MULTIPLE_CHOICE".equals(presentation.get("type").asString())
                    ? "{\"choiceIndex\":0,\"selfAssessment\":\"RECALLED_EASILY\"}"
                    : "{\"answer\":\"일반 SELECT는 스냅샷을 읽어 락을 걸지 않는다\",\"selfAssessment\":\"RECALLED_EASILY\"}";
            JsonNode attempt = send("POST", "/api/practice/presentations/%d/attempts".formatted(presentation.get("presentationId").asLong()), body);
            assertThat(attempt.get("holdReason").isNull()).as("맞는 답은 보류되지 않는다: " + attempt).isTrue();
            ratings.add(attempt.get("rating").asString());
        }
        throw new AssertionError("풀이가 끝나지 않는다: " + practiceId);
    }

    private static int checkedItems(JsonNode gauge) {
        return gauge.get("units").valueStream().mapToInt(unit -> unit.get("gauge").get("checkedItems").asInt()).sum();
    }

    private static Map<Long, Integer> percents(JsonNode gauge) {
        Map<Long, Integer> percents = new HashMap<>();
        gauge.get("units").valueStream().flatMap(unit -> unit.get("items").valueStream())
                .filter(item -> item.get("gauge").get("checked").asBoolean())
                .forEach(item -> percents.put(item.get("memoryItemId").asLong(), item.get("gauge").get("percent").asInt()));
        return percents;
    }

    private interface Probe<T> {
        T value() throws Exception;
    }

    private <T> T await(Probe<T> probe, String what) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (true) {
            T value = probe.value();
            if (value != null) {
                return value;
            }
            assertThat(Instant.now()).as(what + " 대기").isBefore(deadline);
            Thread.sleep(50);
        }
    }

    private JsonNode get(String path) throws Exception {
        return send("GET", path, null);
    }

    private JsonNode send(String method, String path, String json) throws Exception {
        HttpResponse<String> response = exchange(method, path, json);
        assertThat(response.statusCode()).as(method + " " + path + " → " + response.body()).isBetween(200, 299);
        return Json.MAPPER.readTree(response.body());
    }

    private int postStatus(String path) throws Exception {
        return exchange("POST", path, null).statusCode();
    }

    private HttpResponse<String> exchange(String method, String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
