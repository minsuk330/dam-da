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
import com.khack.review.practice.application.AnswerJudgeFixtures;
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
import com.khack.review.practice.domain.QuestionRecheck;
import com.khack.review.practice.domain.QuestionRecheckRepository;
import com.khack.review.practice.domain.RecheckResult;
import com.khack.review.question.application.QuestionQualityQuestions;
import com.khack.review.question.application.port.out.FakeQuestionGenerator;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionStatus;
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
        /** 비어 있지 않으면 다음 행동 선택마다 하나씩 꺼내 답한다(동시 요청이 서로 다른 행동을 고르게). */
        final java.util.Deque<String> nextActions = new java.util.concurrent.ConcurrentLinkedDeque<>();
        /** 있으면 다음 행동 선택에서 모든 요청이 모일 때까지 기다린다. 동시 요청이 모두 저장 전 상태를 보게 한다. */
        final AtomicReference<java.util.concurrent.CyclicBarrier> nextActionBarrier = new AtomicReference<>();
        final AtomicReference<Object> lastState = new AtomicReference<>();
        final java.util.Deque<Object> verdicts = new java.util.concurrent.ConcurrentLinkedDeque<>();
        /** 남은 횟수만큼 문제 품질 검사를 떨어뜨린다(근거 부족). */
        final java.util.concurrent.atomic.AtomicInteger qualityRejections = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger qualityCalls = new java.util.concurrent.atomic.AtomicInteger();
        /** 있으면 문제 품질 검사가 열릴 때까지 기다린다. 재검사가 진행 중인 상태를 만든다. */
        final AtomicReference<java.util.concurrent.CountDownLatch> qualityGate = new AtomicReference<>();

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
                java.util.concurrent.CyclicBarrier barrier = nextActionBarrier.get();
                if (barrier != null) {
                    try {
                        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                String queued = nextActions.poll();
                String next = queued != null ? queued : nextAction.get();
                if (next == null) {
                    throw new JevCallException(500, "테스트: 다음 행동 선택 불가", null);
                }
                return new JevResult("fake", Map.of(NextActionQuestions.NEXT_ACTION,
                        new JevAnswer.Choice(next, Map.of(next, 1.0), 0.9)));
            }
            if (questions.containsKey(QuestionQualityQuestions.GROUNDED)) {
                java.util.concurrent.CountDownLatch gate = qualityGate.get();
                if (gate != null) {
                    try {
                        if (!gate.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                            throw new IllegalStateException("테스트: 품질 검사 대기 시간 초과");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                qualityCalls.incrementAndGet();
            }
            boolean reject = questions.containsKey(QuestionQualityQuestions.GROUNDED)
                    && qualityRejections.getAndUpdate(n -> Math.max(0, n - 1)) > 0;
            return questions.containsKey(QuestionQualityQuestions.GROUNDED)
                    ? new JevResult("fake", Map.of(
                            QuestionQualityQuestions.GROUNDED, new JevAnswer.Noul(reject ? 0.05 : 0.9),
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
    QuestionRecheckRepository rechecks;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    FakeFeedbackContentGenerator generator;

    @Autowired
    TimeTravelClock clock;

    @BeforeEach
    void resetStubs() {
        jev.nextAction.set(null);
        jev.nextActions.clear();
        jev.nextActionBarrier.set(null);
        jev.lastState.set(null);
        jev.verdicts.clear();
        jev.qualityRejections.set(0);
        java.util.concurrent.CountDownLatch gate = jev.qualityGate.getAndSet(null);
        if (gate != null) {
            gate.countDown();
        }
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
                AnswerJudgeQuestions.MISREAD, AnswerJudgeFixtures.misread(misread),
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
        long q2 = second.get("questionId").asLong();
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
        // 첫 학습은 힌트 후 정답이면 Jev에 묻지 않고 다른 문제 몇 개 뒤에 확인 문제를 낸다(§11.3 7단계).
        JsonNode afterHint = decide(p2);
        assertThat(afterHint.get("action").asString()).isEqualTo("RELEARN_TODAY");
        assertThat(afterHint.get("decidedBy").asString()).isEqualTo("RULE");
        assertThat(afterHint.get("path").asString()).isEqualTo("AFTER_HINT");
        assertThat(afterHint.get("recheckQueued").asBoolean()).isTrue();

        List<ReviewLog> logs = reviewLogs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(itemId);
        assertThat(logs).as("힌트 후 정답은 복습 기록을 만들지 않음").singleElement()
                .satisfies(log -> assertThat(log.getRating()).isEqualTo(Rating.AGAIN));
        assertThat(memory.nextReviewAt(itemId)).as("복습 시점 불변").contains(dueBefore);

        // 확인 문제: 큐 끝(다른 문제 뒤)에 새 제시로 나오고 답·힌트·설명은 없다. 지연된 무도움 재확인으로 평가한다.
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).extracting(PracticeQueueEntry::getQuestionId)
                .hasSize(4).endsWith(q1, q2);
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
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(4);

        // 힌트 후 맞힌 2번의 확인 문제: 도움 없이 맞히면 같은 날 재확인으로 평가해 기록한다. 더 편성하지 않는다.
        JsonNode recheck2 = nextPresentation(practiceId);
        long p4 = recheck2.get("presentationId").asLong();
        assertThat(recheck2.get("questionId").asLong()).isEqualTo(q2);
        assertThat(recheck2.get("sameDayRecheck").asBoolean()).isTrue();
        JsonNode confirmed = answer(p4, "{\"choiceIndex\":0,\"selfAssessment\":\"RECALLED_WITH_EFFORT\"}");
        assertThat(confirmed.get("kind").asString()).isEqualTo("DELAYED_RECHECK");
        assertThat(confirmed.get("evaluated").asBoolean()).isTrue();
        assertThat(confirmed.get("correct").asBoolean()).isTrue();
        assertThat(reviewLogs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(itemId)).as("같은 날 재확인 기록").hasSize(2);
        assertThat(decide(p4).get("action").asString()).isEqualTo("ADVANCE");
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(4);
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
    void firstStudyHintedCorrectAnswerAlwaysQueuesARecheck() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "WRONG");
        assertThat(decide(p1).get("action").asString()).isEqualTo("GIVE_HINT");
        JsonNode retry = answerJudged(p1, "{\"answer\":\"표본이 커져도 표준편차는 그대로\"}", "CORRECT");

        // Jev가 넘어가자고 해도 첫 학습은 규칙이 확인 문제를 정한다(Jev에 묻지 않음).
        jev.nextAction.set("advance");
        JsonNode view = decide(p1);

        assertThat(view.get("action").asString()).isEqualTo("RELEARN_TODAY");
        assertThat(view.get("decidedBy").asString()).isEqualTo("RULE");
        assertThat(view.get("path").asString()).isEqualTo("AFTER_HINT");
        assertThat(view.get("recheckQueued").asBoolean()).isTrue();
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(3);
    }

    @Test
    void ambiguousQuestionThatPassesRecheckIsNotAskedAgain() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        long q1 = first.get("questionId").asLong();
        JsonNode a1 = answerJudged(p1, WRONG_TEXT, "AMBIGUOUS");

        JsonNode view = decide(p1);

        // 재검사를 통과해도 같은 문제는 다시 내지 않는다. 원래 문제는 승인으로 남고 변형 문제로 다시 확인한다.
        assertThat(view.get("action").asString()).isEqualTo("GENERATE_VARIANT");
        QuestionRecheck recheck = awaitRecheck(q1);
        awaitRecheckQueued(p1);
        assertThat(recheck.getResult()).isEqualTo(RecheckResult.PASSED);
        assertThat(questionRepository.findById(q1).orElseThrow().getStatus()).isEqualTo(QuestionStatus.APPROVED);
        Question variant = questionRepository.findById(recheck.getVariantQuestionId()).orElseThrow();
        assertThat(variant.getStatus()).isEqualTo(QuestionStatus.APPROVED);
        assertThat(variant.getVariantOfId()).isEqualTo(q1);
        PracticeQueueEntry requeued = practices.findById(practiceId).orElseThrow().getQueue().getLast();
        assertThat(requeued.getRecheckOfPresentationId()).isEqualTo(p1);
        assertThat(requeued.getQuestionId()).isEqualTo(variant.getId()).isNotEqualTo(q1);
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).isEmpty();
    }

    @Test
    void ambiguousHoldRechecksTheQuestionWithoutAFeedbackRequest() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        long q1 = first.get("questionId").asLong();
        jev.qualityRejections.set(1);

        JsonNode held = answerJudged(p1, WRONG_TEXT, "AMBIGUOUS");
        assertThat(held.get("holdReason").asString()).isEqualTo("UNABLE_TO_JUDGE");

        // 피드백을 요청하지 않아도 판정 직후 재검사한다. 떨어지면 폐기하고 변형 문제를 만든다.
        QuestionRecheck recheck = awaitRecheck(q1);
        assertThat(recheck.getResult()).isEqualTo(RecheckResult.RETIRED);
        assertThat(recheck.getPresentationId()).isEqualTo(p1);
        assertThat(questionRepository.findById(q1).orElseThrow().getStatus()).isEqualTo(QuestionStatus.RETIRED);
        Question variant = questionRepository.findById(recheck.getVariantQuestionId()).orElseThrow();
        assertThat(variant.getStatus()).isEqualTo(QuestionStatus.APPROVED);
        assertThat(variant.getVariantOfId()).isEqualTo(q1);

        // 나중에 피드백을 요청하면 다시 검사하지 않고 기록된 변형 문제를 오늘 큐 끝에 낸다.
        int qualityCalls = jev.qualityCalls.get();
        JsonNode view = decide(p1);
        assertThat(view.get("action").asString()).isEqualTo("GENERATE_VARIANT");
        assertThat(view.get("recheckQueued").asBoolean()).isTrue();
        assertThat(practices.findById(practiceId).orElseThrow().getQueue().getLast().getQuestionId()).isEqualTo(variant.getId());
        assertThat(jev.qualityCalls.get()).as("재검사 중복 없음").isEqualTo(qualityCalls);
    }

    @Test
    void feedbackRightAfterAnAmbiguousHoldSharesTheSameRecheck() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        long q1 = first.get("questionId").asLong();
        jev.qualityRejections.set(1);
        int qualityCalls = jev.qualityCalls.get();

        answerJudged(p1, WRONG_TEXT, "AMBIGUOUS");
        JsonNode view = decide(p1);

        QuestionRecheck recheck = awaitRecheck(q1);
        assertThat(recheck.getResult()).isEqualTo(RecheckResult.RETIRED);
        assertThat(view.get("action").asString()).isEqualTo("GENERATE_VARIANT");
        awaitRecheckQueued(p1);
        assertThat(practices.findById(practiceId).orElseThrow().getQueue().getLast().getQuestionId())
                .isEqualTo(recheck.getVariantQuestionId());
        assertThat(questionRepository.findAll()).filteredOn(q -> Long.valueOf(q1).equals(q.getVariantOfId()))
                .as("변형 문제 하나").hasSize(1);
        assertThat(jev.qualityCalls.get() - qualityCalls).as("재검사 1번 + 변형 검사 1번").isEqualTo(2);
    }

    @Test
    void variantFeedbackDoesNotWaitForARunningRecheck() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        JsonNode first = nextPresentation(practiceId);
        long p1 = first.get("presentationId").asLong();
        long q1 = first.get("questionId").asLong();
        int queued = practices.findById(practiceId).orElseThrow().getQueue().size();
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        jev.qualityGate.set(gate);

        answerJudged(p1, WRONG_TEXT, "AMBIGUOUS");
        JsonNode view = decide(p1);

        // 판정 직후 시작한 재검사가 품질 검사에서 멈춰 있어도 피드백은 기다리지 않고 바로 답한다. 변형 문제는 아직 편성 전이다.
        assertThat(view.get("action").asString()).isEqualTo("GENERATE_VARIANT");
        assertThat(view.get("recheckQueued").asBoolean()).isFalse();
        assertThat(rechecks.findByQuestionId(q1)).isEmpty();
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(queued);

        // 재검사가 끝나면 변형 문제를 오늘 큐 끝에 넣는다.
        gate.countDown();
        QuestionRecheck recheck = awaitRecheck(q1);
        awaitRecheckQueued(p1);
        PracticeQueueEntry requeued = practices.findById(practiceId).orElseThrow().getQueue().getLast();
        assertThat(requeued.getQuestionId()).isEqualTo(recheck.getVariantQuestionId()).isNotEqualTo(q1);
        assertThat(requeued.getRecheckOfPresentationId()).isEqualTo(p1);
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(queued + 1);
    }

    /** 피드백 기록에 오늘 다시 묻기가 편성될 때까지 기다린다. 변형 문제는 재검사가 끝난 뒤 따로 편성된다. */
    private void awaitRecheckQueued(long presentationId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!ok("GET", "/api/practice/presentations/%d/feedback".formatted(presentationId), null).get("recheckQueued").asBoolean()) {
            assertThat(Instant.now()).as("확인 문제 편성 대기").isBefore(deadline);
            Thread.sleep(20);
        }
    }

    private QuestionRecheck awaitRecheck(long questionId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (true) {
            java.util.Optional<QuestionRecheck> found = rechecks.findByQuestionId(questionId);
            if (found.isPresent()) {
                return found.get();
            }
            assertThat(Instant.now()).as("재검사 기록 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
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
    void slowGenerationFallsBackToStoredHintWithinTheTimeLimit() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        long p1 = nextPresentation(practiceId).get("presentationId").asLong();
        answerJudged(p1, WRONG_TEXT, "WRONG");
        generator.willDelay(Duration.ofSeconds(5));

        Instant started = Instant.now();
        JsonNode view = decide(p1);

        // 학습자가 기다리는 자리라 생성이 시간 상한(테스트 1초)을 넘으면 기다리지 않고 문제에 저장된 기본 힌트를 쓴다.
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(4));
        assertThat(view.get("action").asString()).isEqualTo("GIVE_HINT");
        assertThat(view.get("hint").asString()).isEqualTo("떠올릴 방향");
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).extracting(AidExposure::getType)
                .containsExactly(AidType.HINT);
    }

    @Test
    void failureWhileSavingLeavesNoAidRecheckOrFeedback() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        long p1 = nextPresentation(practiceId).get("presentationId").asLong();
        answerJudged(p1, WRONG_TEXT, "WRONG");
        jev.nextAction.set("explain_concept");
        // 설명 본문이 컬럼 길이(10,000자)를 넘어 피드백 기록 저장이 실패한다. 도움 노출·확인 문제 편성은 그 전에 일어난다.
        generator.willExplain("가".repeat(10_001));

        clock.travel(Duration.ofSeconds(1));
        HttpResponse<String> failed = send("POST", "/api/practice/presentations/%d/feedback".formatted(p1), null);

        assertThat(failed.statusCode()).as(failed.body()).isNotEqualTo(200);
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).as("도움 노출 롤백").isEmpty();
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).as("확인 문제 편성 롤백").hasSize(2);
        assertThat(ok("GET", "/api/practice/presentations/%d/feedback".formatted(p1), null).get("action").isNull()).isTrue();

        generator.clear();
        JsonNode retried = decide(p1);
        assertThat(retried.get("action").asString()).as("실패한 단계를 건너뛰지 않음").isEqualTo("EXPLAIN_CONCEPT");
        assertThat(retried.get("recheckQueued").asBoolean()).isTrue();
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).hasSize(1);
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(3);
    }

    @Test
    void concurrentFeedbackRequestsQueueTheRecheckOnce() throws Exception {
        long practiceId = startPractice(PracticeKind.DAILY);
        long p1 = nextPresentation(practiceId).get("presentationId").asLong();
        answerJudged(p1, WRONG_TEXT, "WRONG");
        clock.travel(Duration.ofSeconds(1));

        List<HttpResponse<String>> responses = concurrently(p1, 4);

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(200));
        assertThat(practices.findById(practiceId).orElseThrow().getQueue()).hasSize(3)
                .filteredOn(entry -> Long.valueOf(p1).equals(entry.getRecheckOfPresentationId())).hasSize(1);
        assertThat(decide(p1).get("recheckQueued").asBoolean()).isTrue();
    }

    @Test
    void concurrentFeedbackRequestsRecordTheHintOnce() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        long p1 = nextPresentation(practiceId).get("presentationId").asLong();
        answerJudged(p1, WRONG_TEXT, "WRONG");
        clock.travel(Duration.ofSeconds(1));

        List<HttpResponse<String>> responses = concurrently(p1, 4);

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(200));
        assertThat(aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1)).extracting(AidExposure::getType)
                .containsExactly(AidType.HINT);
    }

    @Test
    void concurrentRequestsChoosingDifferentActionsApplyOnlyOne() throws Exception {
        long practiceId = startPractice(PracticeKind.FIRST_STUDY);
        long p1 = nextPresentation(practiceId).get("presentationId").asLong();
        answerJudged(p1, WRONG_TEXT, "WRONG");
        clock.travel(Duration.ofSeconds(1));
        jev.nextActions.addAll(List.of("give_hint", "explain_concept"));
        jev.nextActionBarrier.set(new java.util.concurrent.CyclicBarrier(2));

        List<HttpResponse<String>> responses = concurrently(p1, 2);

        assertThat(responses).allSatisfy(r -> assertThat(r.statusCode()).as(r.body()).isEqualTo(200));
        List<AidExposure> shown = aids.findByPresentationIdOrderByExposedAtAscIdAsc(p1);
        assertThat(shown).as("힌트·설명 중 한쪽만").hasSize(1);
        int queued = practices.findById(practiceId).orElseThrow().getQueue().size();
        assertThat(queued).as("설명이 반영됐을 때만 확인 문제 편성")
                .isEqualTo(shown.getFirst().getType() == AidType.EXPLANATION ? 3 : 2);
    }

    /** 같은 제시에 피드백 요청을 동시에 보낸다. */
    private List<HttpResponse<String>> concurrently(long presentationId, int count) throws Exception {
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(count)) {
            List<java.util.concurrent.Future<HttpResponse<String>>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return send("POST", "/api/practice/presentations/%d/feedback".formatted(presentationId), null);
                }));
            }
            start.countDown();
            List<HttpResponse<String>> responses = new java.util.ArrayList<>();
            for (var future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
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
