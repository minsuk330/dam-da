package com.khack.review.practice.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.application.SessionConfirmationService;
import com.khack.review.analysis.application.UnitReviewQuestions;
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
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevQuestion;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import com.khack.review.memory.application.MemoryStateService;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import com.khack.review.practice.application.AnswerJudgeQuestions;
import com.khack.review.practice.application.LearningGoalService;
import com.khack.review.practice.application.NextActionQuestions;
import com.khack.review.practice.application.NextActionState;
import com.khack.review.practice.application.port.out.FakeFeedbackContentGenerator;
import com.khack.review.practice.application.port.out.FeedbackGenerationException;
import com.khack.review.practice.domain.AidExposure;
import com.khack.review.practice.domain.AidExposureRepository;
import com.khack.review.practice.domain.AidType;
import com.khack.review.practice.domain.LearningGoal;
import com.khack.review.practice.domain.PracticeAttempt;
import com.khack.review.practice.domain.PracticeAttemptRepository;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.practice.domain.PracticeQueueEntry;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.question.application.QuestionQualityQuestions;
import com.khack.review.question.application.port.out.FakeQuestionGenerator;
import io.github.openspacedrepetition.Rating;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

/**
 * 단계적 피드백: 오답 → 힌트 → 재도전 → 개념 설명 → 확인 문제(첫 학습), 틀린 항목 오늘 큐 끝에 한 번 더(매일 학습),
 * 다음 행동 선택(Jev, 가짜), 등급 불변, 지연된 무도움 재확인 평가.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FeedbackIT.Fakes.class)
class FeedbackIT {

    /** 품질 검사·복습 단위 검수는 통과시키고, 다음 행동 선택은 테스트가 정한 값으로 답한다. 값이 없으면 호출 실패(규칙 기본 행동으로 대체). */
    static class StubJev implements JevPort {

        final AtomicReference<String> nextAction = new AtomicReference<>();
        final AtomicReference<Object> lastState = new AtomicReference<>();
        final java.util.Deque<Object> verdicts = new java.util.concurrent.ConcurrentLinkedDeque<>();

        @Override
        public JevResult evaluate(Object state, Map<String, JevQuestion> questions) {
            if (questions.containsKey(AnswerJudgeQuestions.VERDICT)) {
                Object next = verdicts.poll();
                if (next instanceof RuntimeException e) {
                    throw e;
                }
                return (JevResult) next;
            }
            if (questions.containsKey(NextActionQuestions.NEXT_ACTION)) {
                lastState.set(state);
                String next = nextAction.get();
                if (next == null) {
                    throw new JevCallException(500, "테스트: 다음 행동 선택 불가", null);
                }
                return new JevResult("fake", Map.of(NextActionQuestions.NEXT_ACTION,
                        new JevAnswer.Choice(next, Map.of(next, 1.0), 0.9)));
            }
            return questions.containsKey(QuestionQualityQuestions.GROUNDED)
                    ? new JevResult("fake", Map.of(
                            QuestionQualityQuestions.GROUNDED, new JevAnswer.Noul(0.9),
                            QuestionQualityQuestions.CLARITY, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9),
                            QuestionQualityQuestions.DUPLICATE, new JevAnswer.Noul(0.1)))
                    : new JevResult("fake", Map.of(
                            UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(0.9),
                            UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9)));
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        StubJev stubJev() {
            return new StubJev();
        }

        @Bean
        FakeQuestionGenerator fakeQuestionGenerator() {
            return new FakeQuestionGenerator();
        }

        @Bean
        FakeFeedbackContentGenerator fakeFeedbackContentGenerator() {
            return new FakeFeedbackContentGenerator();
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
    PracticeAttemptRepository attempts;

    @Autowired
    AidExposureRepository aids;

    @Autowired
    MemoryStateService memory;

    @Autowired
    ReviewLogRepository reviewLogs;

    @Autowired
    StubJev jev;

    @Autowired
    FakeFeedbackContentGenerator generator;

    @Autowired
    TimeTravelClock clock;

    @BeforeEach
    void resetStubs() {
        jev.nextAction.set(null);
        jev.lastState.set(null);
        jev.verdicts.clear();
        generator.clear();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private long readySession() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        long id = sessions.findByConversationId(conversationId).orElseThrow().getId();
        awaitStatus(id, LearningSessionStatus.AWAITING_CONFIRMATION);
        confirmation.confirm(id);
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

    private long startPractice(PracticeKind kind) throws Exception {
        long practiceId = ok("POST", "/api/sessions/%d/first-study/practice".formatted(readySession()), null).get("practiceId").asLong();
        if (kind == PracticeKind.DAILY) {
            PracticeSession session = practices.findById(practiceId).orElseThrow();
            ReflectionTestUtils.setField(session, "kind", PracticeKind.DAILY);
            practices.save(session);
        }
        return practiceId;
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

    private JsonNode nextPresentation(long practiceId) throws Exception {
        return ok("GET", "/api/practice/%d/next".formatted(practiceId), null).get("presentation");
    }

    private JsonNode answer(long presentationId, String json) throws Exception {
        clock.travel(Duration.ofSeconds(5));
        return ok("POST", "/api/practice/presentations/%d/attempts".formatted(presentationId), json);
    }

    /** 서술형 답을 가짜 Jev 판정(WRONG·CORRECT·UNCERTAIN·AMBIGUOUS·FAILED)과 함께 낸다. 객관식은 코드 채점이라 쓰지 않는다. */
    private JsonNode answerJudged(long presentationId, String json, String outcome) throws Exception {
        jev.verdicts.add(switch (outcome) {
            case "WRONG" -> verdict("not_met", 0.9, 0.05);
            case "CORRECT" -> verdict("met", 0.9, 0.05);
            case "UNCERTAIN" -> verdict("not_met", 0.3, 0.05);
            case "AMBIGUOUS" -> verdict("unable_to_judge", 0.9, 0.05);
            default -> new JevCallException(401, "테스트: 판정 실패", null);
        });
        return answer(presentationId, json);
    }

    private static JevResult verdict(String verdict, double confidence, double misread) {
        return new JevResult("fake-judge", Map.of(
                AnswerJudgeQuestions.VERDICT, new JevAnswer.Choice(verdict, Map.of(verdict, confidence), confidence),
                AnswerJudgeQuestions.OMISSION, new JevAnswer.Noul(0.1),
                AnswerJudgeQuestions.CONTRADICTION, new JevAnswer.Noul(0.1),
                AnswerJudgeQuestions.MISREAD, new JevAnswer.Noul(misread),
                AnswerJudgeQuestions.REPEATS_USER_BELIEF, new JevAnswer.Noul(0.1),
                AnswerJudgeQuestions.OFF_TARGET_ERROR, new JevAnswer.Noul(0.1)));
    }

    private JsonNode decide(long presentationId) throws Exception {
        clock.travel(Duration.ofSeconds(1));
        return ok("POST", "/api/practice/presentations/%d/feedback".formatted(presentationId), null);
    }

    private static final String WRONG_TEXT = "{\"answer\":\"표본이 크면 표준편차도 준다\",\"selfAssessment\":\"RECALLED_EASILY\"}";

    @Test
    void firstStudyFollowsHintRetryExplanationThenDelayedRecheckWithoutChangingRatings() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);

        // 1번(오류 찾기, 서술형): 오답 판정은 #17이 알려 준다. 판정 전에는 피드백을 받을 수 없다.
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        long q1 = first.get("questionId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "WRONG");

        // Jev가 힌트를 건너뛰고 개념 설명을 고른다. 설명은 믿었던 내용·교정과 함께 만들고, 확인 문제를 큐 끝에 편성한다.
        jev.nextAction.set("explain_concept");
        JsonNode explained = decide(p1);
        assertThat(explained.get("action").asString()).isEqualTo("EXPLAIN_CONCEPT");
        assertThat(explained.get("decidedBy").asString()).isEqualTo("JEV");
        assertThat(explained.get("explanation").asString()).contains("표본이 크면 표준편차가 준다");
        assertThat(explained.get("evidenceTurns").get(0).asInt()).isEqualTo(2);
        assertThat(explained.get("recheckQueued").asBoolean()).isTrue();
        assertThat(generator.explanationRequests()).singleElement().satisfies(request -> {
            assertThat(request.userBelief()).isEqualTo("표본이 크면 표준편차가 준다");
            assertThat(request.correction()).isEqualTo("표준오차가 준다");
            assertThat(request.previousAnswers()).containsExactly("표본이 크면 표준편차도 준다");
        });
        assertThat(((NextActionState) jev.lastState.get()).hintShown()).isFalse();
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).extracting(AidExposure::getType)
                .containsExactly(AidType.EXPLANATION);
        assertThat(a1.get("rating").asString()).as("서술형도 제출 시점에 판정·등급이 정해짐(#17)").isEqualTo("AGAIN");
        assertThat(reviewLogs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(
                attempts.findById(a1.get("attemptId").asLong()).orElseThrow().getMemoryItemId()))
                .as("피드백은 등급을 바꾸지 않음").singleElement().satisfies(log -> assertThat(log.getRating()).isEqualTo(Rating.AGAIN));
        assertThat(decide(p1).get("action").asString()).as("설명 뒤 재도전 대기").isEqualTo("RETRY");

        // 설명 뒤 재도전은 평가하지 않는다. 또 틀리면 반복 오답 경로로 표시만 한다.
        JsonNode retry = answerJudged(p1, "{\"answer\":\"모르겠다\"}", "WRONG");
        assertThat(retry.get("kind").asString()).isEqualTo("ASSISTED_RETRY");
        assertThat(retry.get("evaluated").asBoolean()).isFalse();
        JsonNode moveOn = decide(p1);
        assertThat(moveOn.get("action").asString()).isEqualTo("ADVANCE");
        assertThat(moveOn.get("path").asString()).isEqualTo("REPEATED_WRONG");
        assertThat(moveOn.get("prerequisite").isNull()).as("아직 한 제시만 틀림").isTrue();
        assertThat(ok("GET", "/api/practice/presentations/%d/feedback".formatted(p1), null).get("explanation").asString())
                .isEqualTo(explained.get("explanation").asString());

        // 2번(객관식): 코드 채점. 오답은 바로 등급(Again)이 정해진다. Jev가 못 고르면 규칙의 기본 행동(힌트)을 쓴다.
        jev.nextAction.set(null);
        JsonNode second = nextPresentation(practiceId);
        long p2 = second.get("presentationId").asLong();
        JsonNode wrong = answer(p2, "{\"choiceIndex\":1,\"selfAssessment\":\"RECALLED_EASILY\"}");
        assertThat(wrong.get("correct").asBoolean()).isFalse();
        assertThat(wrong.get("rating").asString()).isEqualTo("AGAIN");
        long itemId = attempts.findById(wrong.get("attemptId").asLong()).orElseThrow().getMemoryItemId();
        Instant dueBefore = memory.nextReviewAt(itemId).orElseThrow();

        JsonNode hint = decide(p2);
        assertThat(hint.get("action").asString()).isEqualTo("GIVE_HINT");
        assertThat(hint.get("decidedBy").asString()).isEqualTo("FALLBACK");
        assertThat(hint.get("hint").asString()).startsWith("힌트:");
        assertThat(hint.get("recheckQueued").asBoolean()).as("힌트만으로는 확인 문제를 넣지 않음").isFalse();
        assertThat(decide(p2).get("action").asString()).isEqualTo("RETRY");

        JsonNode retried = answer(p2, "{\"choiceIndex\":0}");
        assertThat(retried.get("kind").asString()).isEqualTo("ASSISTED_RETRY");
        assertThat(retried.get("correct").asBoolean()).isTrue();
        assertThat(retried.get("rating").isNull()).isTrue();
        JsonNode afterHint = decide(p2);
        assertThat(afterHint.get("action").asString()).isEqualTo("ADVANCE");
        assertThat(afterHint.get("path").asString()).isEqualTo("AFTER_HINT");

        List<ReviewLog> logs = reviewLogs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(itemId);
        assertThat(logs).as("힌트 후 정답은 복습 기록을 만들지 않음").singleElement()
                .satisfies(log -> assertThat(log.getRating()).isEqualTo(Rating.AGAIN));
        assertThat(memory.nextReviewAt(itemId)).as("복습 시점 불변").contains(dueBefore);

        // 확인 문제: 큐 끝(다른 문제 뒤)에 새 제시로 나오고 답·힌트·설명은 없다. 지연된 무도움 재확인으로 평가한다.
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).extracting(PracticeQueueEntry::getQuestionId)
                .hasSize(3).last().isEqualTo(q1);
        clock.travel(Duration.ofMinutes(2));
        JsonNode recheck = nextPresentation(practiceId);
        long p3 = recheck.get("presentationId").asLong();
        assertThat(recheck.get("sameDayRecheck").asBoolean()).isTrue();
        assertThat(recheck.get("questionId").asLong()).isEqualTo(q1);
        for (String leaked : List.of("hint", "explanation", "answerCriteria", "modelAnswer")) {
            assertThat(recheck.has(leaked)).as("확인 문제에 %s 없음", leaked).isFalse();
        }
        JsonNode delayed = answerJudged(p3, WRONG_TEXT, "WRONG");
        assertThat(delayed.get("kind").asString()).isEqualTo("DELAYED_RECHECK");
        assertThat(delayed.get("evaluated").asBoolean()).isTrue();
        PracticeAttempt delayedAttempt = attempts.findById(delayed.get("attemptId").asLong()).orElseThrow();
        assertThat(delayedAttempt.isSameDayRecheck()).isTrue();
        assertThat(delayedAttempt.isPriorAidExposed()).as("원래 제시에서 설명을 봄").isTrue();
        assertThat(delayedAttempt.getElapsedSincePriorMs()).isGreaterThanOrEqualTo(Duration.ofMinutes(2).toMillis());

        // 같은 항목을 두 제시에서 틀렸으므로 반복 어려움: 선행 개념을 제안한다. 확인 문제는 다시 편성하지 않는다.
        JsonNode repeated = decide(p3);
        assertThat(repeated.get("action").asString()).isEqualTo("ADVANCE");
        assertThat(repeated.get("prerequisite").get("concept").asString()).startsWith("선행:");
        assertThat(generator.prerequisiteRequests()).hasSize(1);
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(3);
        assertThat(ok("GET", "/api/practice/%d/next".formatted(practiceId), null).get("done").asBoolean()).isTrue();
    }

    @Test
    void dailyWrongItemIsRequeuedOnceAtTheEndOfTodaysQueue() throws Exception {
        long practiceId = startPractice(PracticeKind.DAILY);

        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        long q1 = first.get("questionId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "WRONG");

        // 매일 학습은 힌트·확인 문제 대신 틀린 항목을 그날 큐 끝에 한 번 더 낸다. Jev가 없어도 기본 행동이다.
        JsonNode relearn = decide(p1);
        assertThat(relearn.get("action").asString()).isEqualTo("RELEARN_TODAY");
        assertThat(relearn.get("recheckQueued").asBoolean()).isTrue();
        assertThat(relearn.get("hint").isNull()).isTrue();
        List<PracticeQueueEntry> queue = practices.findById(practiceId).orElseThrow().getQueue();
        assertThat(queue).hasSize(3);
        assertThat(queue.getLast().getQuestionId()).isEqualTo(q1);
        assertThat(queue.getLast().getRecheckOfPresentationId()).isEqualTo(p1);
        assertThat(((NextActionState) jev.lastState.get()).practiceKind()).isEqualTo(PracticeKind.DAILY);

        // 다시 불러도 한 번만 넣는다. 이제 설명을 보여 주는 쪽만 남는다.
        assertThat(decide(p1).get("action").asString()).isEqualTo("ADVANCE");
        jev.nextAction.set("explain_concept");
        assertThat(decide(p1).get("action").asString()).isEqualTo("EXPLAIN_CONCEPT");
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).as("매일 학습의 설명은 큐를 늘리지 않음").hasSize(3);
        jev.nextAction.set(null);

        JsonNode second = nextPresentation(practiceId);
        JsonNode ok = answer(second.get("presentationId").asLong(), "{\"choiceIndex\":0,\"selfAssessment\":\"RECALLED_EASILY\"}");
        assertThat(ok.get("correct").asBoolean()).isTrue();
        assertThat(decide(second.get("presentationId").asLong()).get("action").asString()).isEqualTo("ADVANCE");

        clock.travel(Duration.ofMinutes(1));
        JsonNode recheck = nextPresentation(practiceId);
        long p3 = recheck.get("presentationId").asLong();
        assertThat(recheck.get("sameDayRecheck").asBoolean()).isTrue();
        JsonNode delayed = answerJudged(p3, WRONG_TEXT, "WRONG");
        assertThat(delayed.get("kind").asString()).isEqualTo("DELAYED_RECHECK");
        assertThat(decide(p3).get("action").asString()).as("재확인에서 또 틀려도 다시 넣지 않음").isEqualTo("ADVANCE");
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(3);
        assertThat(ok("GET", "/api/practice/%d/next".formatted(practiceId), null).get("done").asBoolean()).isTrue();
    }

    @Test
    void jevCanChooseRelearnTodayAfterAHintedCorrectAnswer() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "WRONG");
        assertThat(decide(p1).get("action").asString()).isEqualTo("GIVE_HINT");
        JsonNode retry = answerJudged(p1, "{\"answer\":\"표본이 커져도 표준편차는 그대로\"}", "CORRECT");

        jev.nextAction.set("relearn_today");
        JsonNode view = decide(p1);

        assertThat(view.get("action").asString()).isEqualTo("RELEARN_TODAY");
        assertThat(view.get("decidedBy").asString()).isEqualTo("JEV");
        assertThat(view.get("path").asString()).isEqualTo("AFTER_HINT");
        assertThat(view.get("recheckQueued").asBoolean()).isTrue();
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(3);
    }

    @Test
    void ambiguousQuestionIsRecheckedWithoutRatingChange() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "AMBIGUOUS");

        JsonNode view = decide(p1);

        assertThat(view.get("action").asString()).isEqualTo("GENERATE_VARIANT");
        assertThat(view.get("recheckQueued").asBoolean()).isTrue();
        assertThat(practices.findById(practiceId).orElseThrow().getQueue().getLast().getRecheckOfPresentationId()).isEqualTo(p1);
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).isEmpty();
    }

    @Test
    void uncertainJudgmentAsksForConfirmationAndShowsNoHelp() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "UNCERTAIN");

        JsonNode view = decide(p1);

        assertThat(view.get("action").asString()).isEqualTo("REQUEST_CONFIRMATION");
        assertThat(view.get("decidedBy").asString()).isEqualTo("RULE");
        assertThat(view.get("hint").isNull()).isTrue();
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).isEmpty();
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(2);
    }

    @Test
    void failedJudgmentAsksForConfirmationAndMisreadIsTreatedAsAmbiguousQuestion() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        long p1 = nextPresentation(practiceId).get("presentationId").asLong();
        JsonNode failed = answerJudged(p1, WRONG_TEXT, "FAILED");
        assertThat(failed.get("judgment").get("judged").asBoolean()).isFalse();

        JsonNode view = decide(p1);

        assertThat(view.get("action").asString()).as("판정 실패 → UNCERTAIN").isEqualTo("REQUEST_CONFIRMATION");
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).isEmpty();
        assertThat(reviewLogs.findByAttemptId(failed.get("attemptId").asLong())).isEmpty();
    }

    @Test
    void contentFallsBackToStoredHintWhenGenerationFails() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "WRONG");
        generator.willFail(new FeedbackGenerationException("timeout"));

        JsonNode view = decide(p1);

        assertThat(view.get("action").asString()).isEqualTo("GIVE_HINT");
        assertThat(view.get("hint").asString()).isEqualTo("떠올릴 방향");
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).extracting(AidExposure::getType)
                .containsExactly(AidType.HINT);
    }

    @Test
    void feedbackNeedsAnAnswerAndTheCurrentPresentation() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        long p1 = nextPresentation(practiceId).get("presentationId").asLong();

        assertThat(send("POST", "/api/practice/presentations/%d/feedback".formatted(p1), null).statusCode()).isEqualTo(409);
        assertThat(send("POST", "/api/practice/presentations/999999/feedback", null).statusCode()).isEqualTo(404);
        assertThat(ok("GET", "/api/practice/presentations/%d/feedback".formatted(p1), null).get("action").isNull()).isTrue();
    }
}
