package com.khack.review.practice.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.khack.review.analysis.application.SessionConfirmationService;
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
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import com.khack.review.memory.application.MemoryStateService;
import com.khack.review.analysis.application.UnitReviewQuestions;
import com.khack.review.practice.application.LearningGoalService;
import com.khack.review.practice.domain.AttemptKind;
import com.khack.review.practice.domain.LearningGoal;
import com.khack.review.practice.domain.PracticeAttempt;
import com.khack.review.practice.domain.PracticeAttemptRepository;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.practice.domain.QuestionPresentation;
import com.khack.review.practice.domain.QuestionPresentationRepository;
import com.khack.review.practice.domain.SelfAssessment;
import com.khack.review.question.application.QuestionQualityQuestions;
import com.khack.review.question.application.port.out.FakeQuestionGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
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

/** 확인 → 목표 → 문제 준비(가짜 생성기·Jev 통과) → 풀이 시작, 제시, 도움, 답변 제출, 같은 날 재확인. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PracticeIT.Fakes.class)
class PracticeIT {

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        JevPort passingJev() {
            return (state, questions) -> questions.containsKey(QuestionQualityQuestions.GROUNDED)
                    ? new JevResult("fake", Map.of(
                            QuestionQualityQuestions.GROUNDED, new JevAnswer.Noul(0.9),
                            QuestionQualityQuestions.CLARITY, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9),
                            QuestionQualityQuestions.DUPLICATE, new JevAnswer.Noul(0.1)))
                    : new JevResult("fake", Map.of(
                            UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(0.9),
                            UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9)));
        }

        @Bean
        FakeQuestionGenerator fakeQuestionGenerator() {
            return new FakeQuestionGenerator();
        }
    }

    static final SessionInput INPUT = new SessionInput(
            List.of(turn(1, "표준오차가 뭐야?"),
                    new UserTurn(2, "그럼 표본이 크면 표준편차도 줄어?", null, Intent.understanding_check, AiVerdict.corrected, "표준오차가 준다")),
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
    SessionConfirmationService confirmation;

    @Autowired
    LearningGoalService goals;

    @Autowired
    PracticeSessionRepository practices;

    @Autowired
    QuestionPresentationRepository presentations;

    @Autowired
    PracticeAttemptRepository attempts;

    @Autowired
    MemoryStateService memory;

    @Autowired
    TimeTravelClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private long confirmedSession() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        long id = sessions.findByConversationId(conversationId).orElseThrow().getId();
        awaitStatus(id, LearningSessionStatus.AWAITING_CONFIRMATION);
        confirmation.confirm(id);
        return id;
    }

    private long readySession() {
        long id = confirmedSession();
        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.DISTINGUISH), null);
        awaitStatus(id, LearningSessionStatus.QUESTIONS_READY);
        return id;
    }

    private void awaitStatus(long id, LearningSessionStatus status) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (sessions.findById(id).orElseThrow().getStatus() != status) {
            assertThat(Instant.now()).as("%s 대기", status).isBefore(deadline);
            Thread.onSpinWait();
        }
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode ok(String method, String path, String json) throws Exception {
        HttpResponse<String> response = send(method, path, json);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    @Test
    void practiceCannotStartBeforeQuestionsAreReady() throws Exception {
        long id = confirmedSession();

        assertThat(send("POST", "/api/sessions/%d/first-study/practice".formatted(id), null).statusCode()).isEqualTo(409);
        assertThat(send("GET", "/api/practice/999999/next", null).statusCode()).isEqualTo(404);
    }

    @Test
    void firstStudyRecordsPresentationsAidsAndAttemptKinds() throws Exception {
        long sessionId = readySession();

        JsonNode started = ok("POST", "/api/sessions/%d/first-study/practice".formatted(sessionId), null);
        long practiceId = started.get("practiceId").asLong();
        assertThat(started.get("total").asInt()).isEqualTo(2);
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(LearningSessionStatus.IN_PROGRESS);
        assertThat(ok("POST", "/api/sessions/%d/first-study/practice".formatted(sessionId), null).get("practiceId").asLong())
                .as("다시 시작하면 같은 풀이").isEqualTo(practiceId);

        // 1번: 오류 찾기 (서술형). 해석 도움 뒤 첫 답은 무도움 시도다.
        JsonNode first = ok("GET", "/api/practice/%d/next".formatted(practiceId), null).get("presentation");
        long firstId = first.get("presentationId").asLong();
        assertThat(first.get("type").asString()).isEqualTo("ERROR_FINDING");
        assertThat(first.has("answerCriteria")).isFalse();
        assertThat(first.has("modelAnswer")).isFalse();
        assertThat(ok("GET", "/api/practice/%d/next".formatted(practiceId), null).get("presentation").get("presentationId").asLong())
                .as("답하기 전에는 같은 제시").isEqualTo(firstId);
        QuestionPresentation presented = presentations.findById(firstId).orElseThrow();
        memory.retrievability(presented.getMemoryItemId()).ifPresentOrElse(
                r -> assertThat(presented.getPredictedRetrievability()).isCloseTo(r, within(1e-3)),
                () -> assertThat(presented.getPredictedRetrievability()).isNull());

        ok("POST", "/api/practice/presentations/%d/aids".formatted(firstId), "{\"type\":\"INTERPRETATION\"}");
        clock.travel(Duration.ofSeconds(30));
        String attemptsPath = "/api/practice/presentations/%d/attempts".formatted(firstId);
        assertThat(send("POST", attemptsPath, "{\"answer\":\"표준편차는 줄지 않는다\"}").statusCode())
                .as("평가 대상 시도는 자기평가 필수").isEqualTo(400);
        assertThat(send("POST", attemptsPath, "{\"answer\":\"   \",\"selfAssessment\":\"EASY\"}").statusCode()).isEqualTo(400);
        JsonNode unaided = ok("POST", attemptsPath,
                "{\"answer\":\"  표준편차는 줄지 않는다\\n표준오차가 준다  \",\"selfAssessment\":\"EFFORTFUL\",\"responseTimeMs\":999999999,\"firstInputMs\":4000}");
        assertThat(unaided.get("kind").asString()).isEqualTo("FIRST_UNAIDED");
        assertThat(unaided.get("evaluated").asBoolean()).isTrue();
        assertThat(unaided.get("correct").isNull()).as("서술형은 Jev가 판정").isTrue();
        PracticeAttempt firstAttempt = attempts.findById(unaided.get("attemptId").asLong()).orElseThrow();
        assertThat(firstAttempt.getAnswerText()).isEqualTo("표준편차는 줄지 않는다\n표준오차가 준다");
        assertThat(firstAttempt.getSelfAssessment()).isEqualTo(SelfAssessment.EFFORTFUL);
        assertThat(firstAttempt.isInterpretationHelp()).isTrue();
        assertThat(firstAttempt.isPriorAidExposed()).isFalse();
        assertThat(firstAttempt.getServerElapsedMs()).isGreaterThanOrEqualTo(30_000);
        assertThat(firstAttempt.getResponseTimeMs()).as("클라이언트 값은 서버 경과로 상한").isEqualTo(firstAttempt.getServerElapsedMs());
        assertThat(firstAttempt.getFirstInputMs()).isEqualTo(4000);
        assertThat(firstAttempt.getPredictedRetrievability()).isEqualTo(presented.getPredictedRetrievability());
        assertThat(firstAttempt.isSameDayRecheck()).isFalse();

        assertThat(send("POST", attemptsPath, "{\"answer\":\"다시\",\"selfAssessment\":\"EASY\"}").statusCode())
                .as("도움 없이 두 번 답할 수 없음").isEqualTo(409);
        ok("POST", "/api/practice/presentations/%d/aids".formatted(firstId), "{\"type\":\"HINT\"}");
        clock.travel(Duration.ofSeconds(10));
        JsonNode aided = ok("POST", attemptsPath, "{\"answer\":\"표본이 커져도 표준편차는 그대로\",\"responseTimeMs\":8000}");
        assertThat(aided.get("kind").asString()).isEqualTo("AFTER_AID");
        assertThat(aided.get("evaluated").asBoolean()).isFalse();
        PracticeAttempt aidedAttempt = attempts.findById(aided.get("attemptId").asLong()).orElseThrow();
        assertThat(aidedAttempt.isPriorAidExposed()).isTrue();
        assertThat(aidedAttempt.getElapsedSincePriorMs()).isBetween(10_000L, 15_000L);
        assertThat(aidedAttempt.getResponseTimeMs()).isEqualTo(8000);

        // 2번: 객관식은 코드가 채점한다.
        JsonNode second = ok("GET", "/api/practice/%d/next".formatted(practiceId), null).get("presentation");
        long secondId = second.get("presentationId").asLong();
        assertThat(second.get("type").asString()).isEqualTo("MULTIPLE_CHOICE");
        assertThat(second.get("choices")).hasSize(4);
        assertThat(send("POST", "/api/practice/presentations/%d/aids".formatted(firstId), "{\"type\":\"HINT\"}").statusCode())
                .as("지나간 제시").isEqualTo(409);
        String secondPath = "/api/practice/presentations/%d/attempts".formatted(secondId);
        assertThat(send("POST", secondPath, "{\"choiceIndex\":9,\"selfAssessment\":\"EASY\"}").statusCode()).isEqualTo(400);
        JsonNode choice = ok("POST", secondPath, "{\"choiceIndex\":0,\"selfAssessment\":\"EASY\"}");
        assertThat(choice.get("kind").asString()).isEqualTo("FIRST_UNAIDED");
        assertThat(choice.get("correct").asBoolean()).isTrue();

        // 같은 날 재확인(#20이 편성): 1번 문제를 큐 끝에 넣는다.
        PracticeSession practice = practices.findById(practiceId).orElseThrow();
        practice.enqueueRecheck(presented.getQuestionId(), firstId);
        practices.save(practice);
        clock.travel(Duration.ofMinutes(2));
        JsonNode recheck = ok("GET", "/api/practice/%d/next".formatted(practiceId), null).get("presentation");
        assertThat(recheck.get("sameDayRecheck").asBoolean()).isTrue();
        assertThat(recheck.get("questionId").asLong()).isEqualTo(presented.getQuestionId());
        JsonNode delayed = ok("POST", "/api/practice/presentations/%d/attempts".formatted(recheck.get("presentationId").asLong()),
                "{\"answer\":\"표준편차는 줄지 않는다\",\"selfAssessment\":\"EASY\"}");
        assertThat(delayed.get("kind").asString()).isEqualTo(AttemptKind.DELAYED_RECHECK.name());
        assertThat(delayed.get("evaluated").asBoolean()).isTrue();
        PracticeAttempt delayedAttempt = attempts.findById(delayed.get("attemptId").asLong()).orElseThrow();
        assertThat(delayedAttempt.isSameDayRecheck()).isTrue();
        assertThat(delayedAttempt.isPriorAidExposed()).as("원래 제시에서 힌트를 봄").isTrue();
        assertThat(delayedAttempt.getElapsedSincePriorMs()).isGreaterThanOrEqualTo(Duration.ofMinutes(2).toMillis());

        JsonNode done = ok("GET", "/api/practice/%d/next".formatted(practiceId), null);
        assertThat(done.get("done").asBoolean()).isTrue();
        assertThat(practices.findById(practiceId).orElseThrow().getCompletedAt()).isNotNull();
    }
}
