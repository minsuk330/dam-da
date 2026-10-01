package com.khack.review.practice.adapter.in.web;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.application.SessionConfirmationService;
import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.analysis.application.SessionItemsQuery.ActiveItem;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItemKind;
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
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.TimeTravelClock;
import com.khack.review.common.json.Json;
import com.khack.review.memory.application.MemoryStateService;
import com.khack.review.memory.application.RatingPolicyProperties;
import com.khack.review.memory.application.ReviewRecordService;
import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.memory.domain.DailyQueueBuilt;
import com.khack.review.memory.domain.NewItems;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.ReviewContext;
import com.khack.review.memory.domain.SelfAssessment;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;

/**
 * 오늘의 학습 (스펙 §6.4.4, S2-1~4): 신규 분리와 하루 1개, 시간 이동에 따른 복습 후보, 당일 재학습, 보류, 시작과 문제 선택.
 * 테스트마다 새 컨텍스트(새 DB)를 쓴다. 매일 학습 큐는 사용자의 모든 세션을 보므로 다른 테스트의 세션이 섞이면 안 된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import({PracticeIT.Fakes.class, DailyPracticeIT.Events.class})
class DailyPracticeIT {

    @TestConfiguration
    static class Events {

        @Bean
        Built built() {
            return new Built();
        }
    }

    static class Built {

        final List<DailyQueueBuilt> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(DailyQueueBuilt event) {
            events.add(event);
        }
    }

    static final AtomicLong ATTEMPT_IDS = new AtomicLong(700_000);

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
    SessionItemsQuery sessionItems;

    @Autowired
    ReviewRecordService reviews;

    @Autowired
    MemoryStateService memory;

    @Autowired
    QuestionRepository questions;

    @Autowired
    RatingPolicyProperties rating;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    TimeTravelClock clock;

    @Autowired
    PracticeSessionRepository practices;

    @Autowired
    Built built;

    @Autowired
    com.khack.review.memory.application.DailyQueueService dailyQueue;

    @Autowired
    PracticeIT.RoutingJev jev;

    @Autowired
    com.khack.review.memory.domain.ReviewLogRepository reviewLogs;

    @AfterEach
    void resetClock() {
        clock.reset();
        jev.answers.clear();
        jev.answerStates.clear();
    }

    /** 단위마다 핵심 사실 목록(종류, 근거 발화)을 가진 세션 입력. 발화는 1..turns까지 일반 질문이다. */
    static SessionInput input(String topic, int turns, List<ReviewUnit> units) {
        List<UserTurn> userTurns = new ArrayList<>();
        for (int i = 1; i <= turns; i++) {
            userTurns.add(turn(i, "질문 " + i));
        }
        return new SessionInput(userTurns, units, topic);
    }

    static ReviewUnit unit(String title, KeyPoint... points) {
        return new ReviewUnit(title, List.of(points), null);
    }

    static KeyPoint fact(String text, int turn) {
        return point(text, turn);
    }

    static KeyPoint warning(String text, int turn) {
        return new KeyPoint(text, List.of(turn), FactKind.warning);
    }

    /** 확인하고 첫 학습까지 끝낸 세션. 첫 풀이가 없는 항목은 첫 학습 상한으로 다루지 못한 항목처럼 신규 후보가 된다. */
    private long confirmedSession(SessionInput input) {
        long id = confirmedOnly(input);
        firstStudy(id, true);
        return id;
    }

    /** 확인만 한 세션(학습 목표 선택·첫 학습 전). */
    private long confirmedOnly(SessionInput input) {
        SavedSession saved = intake.intake(input);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        long id = sessions.findByConversationId(conversationId).orElseThrow().getId();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (sessions.findById(id).orElseThrow().getStatus() != LearningSessionStatus.AWAITING_CONFIRMATION) {
            assertThat(Instant.now()).as("확인 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
        confirmation.confirm(id);
        return id;
    }

    /** 세션의 첫 학습 풀이를 만든다(문제 없이). {@code done}이면 끝낸 것으로 한다. */
    private PracticeSession firstStudy(long sessionId, boolean done) {
        PracticeSession practice = PracticeSession.firstStudy(currentUser.id(), sessionId, List.of(), clock.instant());
        if (done) {
            practice.complete(clock.instant());
        }
        return practices.save(practice);
    }

    private static NewItems allNewAs(QuestionType type) {
        return new NewItems() {
            @Override
            public boolean admits(Long sessionId) {
                return true;
            }

            @Override
            public QuestionType typeFor(Long sessionId, Long memoryItemId, MemoryItemKind kind) {
                return type;
            }
        };
    }

    private JsonNode ok(String method, String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    private JsonNode post(String path, String json) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    private static List<JsonNode> items(JsonNode daily) {
        return StreamSupport.stream(daily.get("items").spliterator(), false).toList();
    }

    private ActiveItem item(long sessionId, MemoryItemKind kind, int nth) {
        return sessionItems.activeItemsOf(currentUser.id()).stream()
                .filter(i -> i.sessionId() == sessionId && i.kind() == kind).skip(nth).findFirst().orElseThrow();
    }

    /** 첫 풀이 기록을 지금 시각으로 남긴다(등급은 판정으로 정한다: MET는 성공, NOT_MET는 Again). */
    private void solve(ActiveItem item, AnswerVerdict verdict) {
        ReviewContext context = new ReviewContext(currentUser.id(), item.memoryItemId(), 1L, ATTEMPT_IDS.incrementAndGet(),
                clock.instant(), null, false, null, false, null, null);
        reviews.record(context, new RatingInput(AttemptKind.FIRST_UNASSISTED, verdict, 0.9, false, 0, false,
                SelfAssessment.RECALLED_WITH_EFFORT, QuestionType.SHORT_ANSWER, Duration.ofSeconds(20)));
    }

    @Test
    void newItemsAreSeparatedCappedAtThreeAndOnePerUnitThenStartPicksQuestions() throws Exception {
        // 단위 4개. 첫 단위는 헷갈린 지점 + warning + fact(같은 단위라 하루 1개만).
        SessionInput confusionInput = new SessionInput(List.of(
                turn(1, "표준오차가 뭐야?"),
                new UserTurn(2, "표본이 크면 표준편차도 줄어?", null, Intent.understanding_check, AiVerdict.corrected, "표준오차가 준다"),
                turn(3, "분산은?"), turn(4, "가설검정은?"), turn(5, "신뢰구간은?")),
                List.of(new ReviewUnit("표준오차", List.of(fact("표준오차는 표본평균의 퍼짐", 1), warning("표본이 커져도 표준편차는 줄지 않는다", 2)),
                                List.of(new ConfusionPoint(2, "표본이 크면 표준편차가 준다"))),
                        unit("분산", fact("분산은 퍼짐의 제곱", 3)),
                        unit("가설검정", fact("귀무가설을 기각한다", 4)),
                        unit("신뢰구간", fact("구간이 모수를 덮는다", 5))),
                "경영통계");
        long sessionId = confirmedSession(confusionInput);

        JsonNode today = ok("GET", "/api/daily");

        assertThat(today.get("started").asBoolean()).isFalse();
        assertThat(today.get("reviewCount").asInt()).as("첫 풀이 전이라 복습은 없다").isZero();
        assertThat(today.get("newCount").asInt()).isEqualTo(3);
        assertThat(items(today)).extracting(i -> i.get("kind").asString()).containsExactly("CONFUSION", "FACT", "FACT");
        assertThat(items(today)).extracting(i -> i.get("source").asString()).containsOnly("NEW");
        assertThat(items(today)).extracting(i -> i.get("questionType").asString())
                .containsExactly("ERROR_FINDING", "SHORT_ANSWER", "SHORT_ANSWER");
        assertThat(today.get("estimatedSeconds").asLong()).isEqualTo(60 + 40 + 40);
        assertThat(today.get("carriedOver").asInt()).as("같은 단위 2개 + 신규 상한 1개").isEqualTo(3);
        assertThat(built.events).as("조회만으로는 발행하지 않는다").isEmpty();

        JsonNode started = ok("POST", "/api/daily/start");

        assertThat(started.get("started").asBoolean()).isTrue();
        long practiceId = started.get("practiceId").asLong();
        assertThat(started.get("total").asInt()).isEqualTo(3);
        assertThat(started.get("newCount").asInt()).isEqualTo(3);
        assertThat(started.get("estimatedSeconds").asLong()).isEqualTo(140);
        assertThat(built.events).hasSize(1);
        DailyQueueBuilt event = built.events.getFirst();
        assertThat(event.userId()).isEqualTo(currentUser.id());
        assertThat(event.newCount()).isEqualTo(3);
        assertThat(event.reviewCount()).isZero();
        assertThat(event.itemIds()).hasSize(3);
        assertThat(event.estimatedTime()).isEqualTo(Duration.ofSeconds(140));
        // 승인 문제가 없던 신규 항목은 학습 목표 유형(기본)의 문제를 새로 만들어 쓴다.
        assertThat(questions.findBySessionIdOrderByIdAsc(sessionId)).filteredOn(q -> event.itemIds().contains(q.getMemoryItemId()))
                .extracting(q -> q.getType().name()).containsExactlyInAnyOrder("ERROR_FINDING", "SHORT_ANSWER", "SHORT_ANSWER");

        JsonNode first = ok("GET", "/api/practice/%d/next".formatted(practiceId)).get("presentation");
        assertThat(first.get("type").asString()).isEqualTo("ERROR_FINDING");
        assertThat(first.get("total").asInt()).isEqualTo(3);

        JsonNode again = ok("POST", "/api/daily/start");
        assertThat(again.get("practiceId").asLong()).as("다시 시작하면 오늘 풀이를 이어서").isEqualTo(practiceId);
        assertThat(built.events).hasSize(1);
        assertThat(ok("GET", "/api/daily").get("practiceId").asLong()).isEqualTo(practiceId);
    }

    @Test
    void timeTravelMakesItemsDueMixesSessionsAndKeepsOnePerUnit() throws Exception {
        long sessionA = confirmedSession(input("DB 잠금", 3, List.of(
                unit("락", fact("레코드 락", 1), warning("갭 락은 삽입을 막는다", 2)),
                unit("MVCC", fact("스냅샷 읽기", 3)))));
        long sessionB = confirmedSession(input("경영통계", 1, List.of(unit("표준오차", fact("표본평균의 퍼짐", 1)))));
        ActiveItem lockFact = item(sessionA, MemoryItemKind.FACT, 0);
        ActiveItem lockWarning = item(sessionA, MemoryItemKind.WARNING, 0);
        ActiveItem mvcc = item(sessionA, MemoryItemKind.FACT, 1);
        ActiveItem se = item(sessionB, MemoryItemKind.FACT, 0);
        solve(lockFact, AnswerVerdict.MET);
        solve(lockWarning, AnswerVerdict.MET);
        solve(mvcc, AnswerVerdict.NOT_MET);
        solve(se, AnswerVerdict.MET);

        // 바로 지금: R이 높아 복습 후보는 없다. 방금 틀린 항목만 당일 재학습으로 나온다. 신규도 없다.
        JsonNode now = ok("GET", "/api/daily");
        assertThat(items(now)).hasSize(1);
        assertThat(items(now).getFirst().get("memoryItemId").asLong()).isEqualTo(mvcc.memoryItemId());
        assertThat(items(now).getFirst().get("relearnToday").asBoolean()).isTrue();
        assertThat(items(now).getFirst().get("source").asString()).isEqualTo("REVIEW");

        // 40일 뒤: 모두 R이 목표 유지율 아래다. 단위마다 1개, 두 세션이 섞인다.
        clock.travel(Duration.ofDays(40));
        JsonNode later = ok("GET", "/api/daily");
        List<JsonNode> queue = items(later);
        assertThat(queue).extracting(i -> i.get("memoryItemId").asLong())
                .containsExactlyInAnyOrder(lockWarning.memoryItemId(), mvcc.memoryItemId(), se.memoryItemId());
        assertThat(queue).as("같은 단위의 형제(fact)는 이월").noneMatch(i -> i.get("memoryItemId").asLong() == lockFact.memoryItemId());
        assertThat(queue).extracting(i -> i.get("learningSessionId").asLong()).containsExactlyInAnyOrder(sessionA, sessionA, sessionB);
        assertThat(queue).allSatisfy(i -> {
            assertThat(i.get("source").asString()).isEqualTo("REVIEW");
            assertThat(i.get("relearnToday").asBoolean()).as("40일 전의 Again은 당일 재학습이 아니다").isFalse();
            assertThat(i.get("retrievability").asDouble()).isLessThan(0.9);
        });
        long expectedSeconds = queue.stream()
                .mapToLong(i -> rating.referenceTimes().get(QuestionType.valueOf(i.get("questionType").asString())).toSeconds()).sum();
        assertThat(later.get("estimatedSeconds").asLong()).isEqualTo(expectedSeconds).isLessThanOrEqualTo(300);
        assertThat(later.get("carriedOver").asInt()).isEqualTo(1);

        // 시작하면 항목마다 승인 문제를 만들어 오늘 풀이를 연다. 같은 날 다시 시작해도 하나다.
        JsonNode started = ok("POST", "/api/daily/start");
        assertThat(started.get("total").asInt()).isEqualTo(3);
        assertThat(started.get("reviewCount").asInt()).isEqualTo(3);
        assertThat(built.events).hasSize(1);
        assertThat(built.events.getFirst().reviewCount()).isEqualTo(3);
        Set<Long> questioned = questions.findAll().stream().map(q -> q.getMemoryItemId()).collect(Collectors.toSet());
        assertThat(questioned).contains(lockWarning.memoryItemId(), mvcc.memoryItemId(), se.memoryItemId());
    }

    @Test
    void largeBacklogAfterABreakStaysWithinTheDailyBudget() throws Exception {
        List<ReviewUnit> units = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            units.add(unit("단위" + i, fact("사실" + i, i)));
        }
        long session = confirmedSession(input("쌓인 복습", 24, units));
        sessionItems.activeItemsOf(currentUser.id()).stream().filter(i -> i.sessionId() == session)
                .forEach(i -> solve(i, AnswerVerdict.MET));

        clock.travel(Duration.ofDays(120));
        JsonNode today = ok("GET", "/api/daily");

        assertThat(items(today)).isNotEmpty().hasSizeLessThan(24);
        assertThat(today.get("estimatedSeconds").asLong()).isLessThanOrEqualTo(300);
        assertThat(today.get("carriedOver").asInt()).as("처리하지 못한 항목은 다음 큐로").isEqualTo(24 - items(today).size());

        // 오늘 푼 항목은 R이 회복돼 다음 날 큐에서 빠지고, 이월된 항목이 남는다.
        for (JsonNode queued : items(today)) {
            solve(sessionItems.activeItemsOf(currentUser.id()).stream()
                    .filter(i -> i.memoryItemId() == queued.get("memoryItemId").asLong()).findFirst().orElseThrow(), AnswerVerdict.MET);
        }
        clock.travel(Duration.ofDays(1));
        JsonNode next = ok("GET", "/api/daily");
        Set<Long> yesterday = items(today).stream().map(i -> i.get("memoryItemId").asLong()).collect(Collectors.toSet());
        assertThat(items(next)).isNotEmpty().noneMatch(i -> yesterday.contains(i.get("memoryItemId").asLong()));
    }

    @Test
    void heldItemsAreCandidatesUntilPausedAndPausedItemsAreLeftOut() throws Exception {
        long sessionId = confirmedSession(input("보류", 2, List.of(unit("A", fact("가", 1)), unit("B", fact("나", 2)))));
        ActiveItem a = item(sessionId, MemoryItemKind.FACT, 0);
        ActiveItem b = item(sessionId, MemoryItemKind.FACT, 1);
        solve(a, AnswerVerdict.MET);
        solve(b, AnswerVerdict.MET);

        // 오늘 푼 단위는 하루 1개 규칙으로 막히므로, 하루 지난 뒤 보류를 기록한다.
        clock.travel(Duration.ofDays(1));
        memory.hold(currentUser.id(), a.memoryItemId());
        JsonNode held = ok("GET", "/api/daily");
        JsonNode heldItem = items(held).stream().filter(i -> i.get("memoryItemId").asLong() == a.memoryItemId()).findFirst().orElseThrow();
        assertThat(heldItem.get("held").asBoolean()).isTrue();

        memory.hold(currentUser.id(), a.memoryItemId());
        assertThat(items(ok("GET", "/api/daily"))).as("연속 보류는 자동 출제에서 뺀다")
                .noneMatch(i -> i.get("memoryItemId").asLong() == a.memoryItemId());
        clock.travel(Duration.ofDays(60));
        assertThat(items(ok("GET", "/api/daily")).stream().map(i -> i.get("memoryItemId").asLong()))
                .contains(b.memoryItemId()).doesNotContain(a.memoryItemId());
    }

    /** 서술형을 매일 학습에서 풀면 Jev 판정 → 등급이 바로 남아, 신규가 복습 항목으로 바뀌고 틀리면 당일 재학습이 된다. */
    @Test
    void essayAnsweredInDailyPracticeLeavesARatingAndBecomesReviewAndRelearn() throws Exception {
        long sessionId = confirmedSession(input("서술", 1, List.of(unit("표준오차", fact("표본평균의 퍼짐", 1)))));
        ActiveItem item = item(sessionId, MemoryItemKind.FACT, 0);
        assertThat(items(ok("GET", "/api/daily")).getFirst().get("source").asString()).isEqualTo("NEW");
        long practiceId = ok("POST", "/api/daily/start").get("practiceId").asLong();
        JsonNode presentation = ok("GET", "/api/practice/%d/next".formatted(practiceId)).get("presentation");
        assertThat(presentation.get("type").asString()).isEqualTo("SHORT_ANSWER");

        jev.answers.add(PracticeIT.judged("not_met", 0.9, 0.85));
        clock.travel(Duration.ofSeconds(20));
        JsonNode attempt = post("/api/practice/presentations/%d/attempts".formatted(presentation.get("presentationId").asLong()),
                "{\"answer\":\"모르겠다\",\"selfAssessment\":\"RECALLED_WITH_EFFORT\"}");

        assertThat(attempt.get("rating").asString()).isEqualTo("AGAIN");
        assertThat(reviewLogs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(item.memoryItemId())).hasSize(1);
        // 오늘 풀이가 이미 있어 GET은 그 풀이를 돌려주므로, 다시 큐를 만들면 어떻게 되는지는 서비스로 본다.
        var queued = dailyQueue.preview(currentUser.id(), allNewAs(QuestionType.SHORT_ANSWER), Set.of()).plan().entries();
        assertThat(queued).hasSize(1);
        assertThat(queued.getFirst().itemId()).isEqualTo(item.memoryItemId());
        assertThat(queued.getFirst().source().name()).as("첫 풀이 뒤에는 신규가 아니다").isEqualTo("REVIEW");
        assertThat(queued.getFirst().relearnToday()).isTrue();
    }

    /** 문제가 모호해 보류되면(MISREAD) 다음 큐에서는 같은 문제를 피해 변형 문제를 낸다. */
    @Test
    void ambiguousHeldQuestionIsNotAskedAgain() throws Exception {
        long sessionId = confirmedSession(input("보류", 1, List.of(unit("A", fact("가", 1)))));
        ActiveItem item = item(sessionId, MemoryItemKind.FACT, 0);
        long practiceId = ok("POST", "/api/daily/start").get("practiceId").asLong();
        JsonNode presentation = ok("GET", "/api/practice/%d/next".formatted(practiceId)).get("presentation");
        long firstQuestion = presentation.get("questionId").asLong();

        jev.answers.add(PracticeIT.judged("not_met", 0.9, 0.1, 0.9, 0.05));
        clock.travel(Duration.ofSeconds(20));
        JsonNode attempt = post("/api/practice/presentations/%d/attempts".formatted(presentation.get("presentationId").asLong()),
                "{\"answer\":\"질문이 이상하다\",\"selfAssessment\":\"RECALLED_WITH_EFFORT\"}");
        assertThat(attempt.get("holdReason").asString()).isEqualTo("MISREAD");

        clock.travel(Duration.ofDays(1));
        JsonNode next = ok("GET", "/api/daily");
        assertThat(items(next).getFirst().get("held").asBoolean()).isTrue();
        long nextPractice = ok("POST", "/api/daily/start").get("practiceId").asLong();
        JsonNode again = ok("GET", "/api/practice/%d/next".formatted(nextPractice)).get("presentation");
        assertThat(again.get("questionId").asLong()).as("보류된 문제 대신 변형").isNotEqualTo(firstQuestion);
        assertThat(questions.findByMemoryItemIdOrderByIdAsc(item.memoryItemId())).hasSizeGreaterThanOrEqualTo(2);
    }

    /** 신규 후보는 첫 학습을 끝낸 세션에서만 받는다. 확인만 했거나 첫 학습 도중인 세션의 항목은 첫 학습에서 다룬다(#78). */
    @Test
    void newItemsComeOnlyFromSessionsWhoseFirstStudyIsDone() throws Exception {
        long notStarted = confirmedOnly(input("목표 전", 1, List.of(unit("A", fact("가", 1)))));
        long inProgress = confirmedOnly(input("첫 학습 중", 1, List.of(unit("B", fact("나", 1)))));
        PracticeSession running = firstStudy(inProgress, false);
        long done = confirmedOnly(input("첫 학습 끝", 1, List.of(unit("C", fact("다", 1)))));
        firstStudy(done, true);

        assertThat(items(ok("GET", "/api/daily"))).as("첫 학습을 끝낸 세션의 첫 풀이 없는 항목(상한으로 넘친 항목)만")
                .extracting(i -> i.get("memoryItemId").asLong())
                .containsExactly(item(done, MemoryItemKind.FACT, 0).memoryItemId());

        running.complete(clock.instant());
        practices.save(running);
        assertThat(items(ok("GET", "/api/daily"))).as("첫 학습을 끝내면 그 세션도 받는다")
                .extracting(i -> i.get("memoryItemId").asLong())
                .containsExactlyInAnyOrder(item(done, MemoryItemKind.FACT, 0).memoryItemId(),
                        item(inProgress, MemoryItemKind.FACT, 0).memoryItemId());
        assertThat(items(ok("GET", "/api/daily"))).extracting(i -> i.get("memoryItemId").asLong())
                .doesNotContain(item(notStarted, MemoryItemKind.FACT, 0).memoryItemId());
    }

    @Test
    void emptyDayDoesNotCreateAPractice() throws Exception {
        JsonNode today = ok("GET", "/api/daily");
        assertThat(items(today)).isEmpty();

        JsonNode started = ok("POST", "/api/daily/start");

        assertThat(started.get("started").asBoolean()).isFalse();
        assertThat(started.get("practiceId").isNull()).isTrue();
        assertThat(started.get("total").asInt()).isZero();
        assertThat(Map.of("events", built.events.size())).containsEntry("events", 1);
    }
}
