package com.khack.review.question.application;

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
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import com.khack.review.practice.application.LearningGoalService;
import com.khack.review.practice.domain.LearningGoal;
import com.khack.review.question.application.port.out.FakeQuestionGenerator;
import com.khack.review.question.application.port.out.GeneratedQuestion;
import com.khack.review.question.application.port.out.QuestionGenerationException;
import com.khack.review.question.application.port.out.UnitQuestionResult;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;

/** 확인 완료 → 학습 목표 → 비동기 문제 생성·품질 검사 → 문제 준비. 재생성·출제 보류·변형·재검사. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"review.analysis.retry-delay=1ms", "review.question.retry-delay=1ms"})
@Import(QuestionGenerationIT.Fakes.class)
class QuestionGenerationIT {

    static final JevResult UNIT_PASS = new JevResult("fake", Map.of(
            UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(0.9),
            UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9)));
    static final JevResult QUALITY_PASS = quality(0.9);
    static final JevResult QUALITY_FAIL = quality(0.2);

    static JevResult quality(double grounded) {
        return new JevResult("fake", Map.of(
                QuestionQualityQuestions.GROUNDED, new JevAnswer.Noul(grounded),
                QuestionQualityQuestions.CLARITY, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9),
                QuestionQualityQuestions.DUPLICATE, new JevAnswer.Noul(0.1)));
    }

    /** 복습 단위 검수는 항상 통과, 문제 품질 검사는 순서대로 지정한 결과(다 쓰면 기본값). */
    static class RoutingJev implements JevPort {

        final Deque<JevResult> quality = new ArrayDeque<>();
        volatile JevResult qualityDefault = QUALITY_PASS;
        /** 있으면 다음 품질 검사 한 번이 이 문이 열릴 때까지 멈춘다(들어오면 {@code holding}을 연다). */
        final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CountDownLatch> hold = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch holding = new java.util.concurrent.CountDownLatch(1);

        @Override
        public JevResult evaluate(Object state, Map<String, com.khack.review.common.application.port.out.JevQuestion> questions) {
            if (!questions.containsKey(QuestionQualityQuestions.GROUNDED)) {
                return UNIT_PASS;
            }
            java.util.concurrent.CountDownLatch gate = hold.getAndSet(null);
            if (gate != null) {
                holding.countDown();
                try {
                    gate.await(15, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (this) {
                JevResult next = quality.poll();
                return next != null ? next : qualityDefault;
            }
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        RoutingJev routingJev() {
            return new RoutingJev();
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
    QuestionGenerationService generation;

    @Autowired
    QuestionRepository questions;

    @Autowired
    RoutingJev jev;

    @Autowired
    FakeQuestionGenerator generator;

    @BeforeEach
    void reset() {
        generator.clear();
        jev.quality.clear();
        jev.qualityDefault = QUALITY_PASS;
    }

    private long confirmedSession() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        long id = awaitStatus(() -> sessions.findByConversationId(conversationId).orElseThrow().getId(),
                LearningSessionStatus.AWAITING_CONFIRMATION);
        confirmation.confirm(id);
        return id;
    }

    private long awaitStatus(java.util.function.Supplier<Long> sessionId, LearningSessionStatus status) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (true) {
            Long id = sessionId.get();
            if (sessions.findById(id).orElseThrow().getStatus() == status) {
                return id;
            }
            assertThat(Instant.now()).as("%s 대기", status).isBefore(deadline);
            Thread.onSpinWait();
        }
    }

    private JsonNode firstStudy(long sessionId) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:%d/api/sessions/%d/first-study".formatted(port, sessionId))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return Json.MAPPER.readTree(response.body());
    }

    @Test
    void goalsTriggerGenerationAndTheSessionBecomesReadyWithApprovedQuestionsOnly() throws Exception {
        long id = confirmedSession();

        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.CONDITION), null);
        awaitStatus(() -> id, LearningSessionStatus.QUESTIONS_READY);

        JsonNode body = firstStudy(id);
        assertThat(body.get("generating").asBoolean()).isFalse();
        assertThat(body.get("planned").asInt()).isEqualTo(2);
        assertThat(body.get("questions")).hasSize(2);
        assertThat(body.get("held")).isEmpty();
        JsonNode first = body.get("questions").get(0);
        assertThat(first.get("type").asString()).isEqualTo("ERROR_FINDING");
        assertThat(first.has("answerCriteria")).as("정답 기준은 내보내지 않음").isFalse();
        assertThat(first.has("modelAnswer")).isFalse();
        assertThat(generator.calls()).as("같은 복습 단위는 한 번에 요청").hasSize(1);
        assertThat(generator.calls().get(0).targets()).extracting(t -> t.type()).containsExactly(QuestionType.ERROR_FINDING, QuestionType.CASE_JUDGMENT);
        assertThat(generator.calls().get(0).keyPoints()).as("단위의 기억 항목 전체를 오답 재료로 보냄").hasSize(3);
        assertThat(generator.targets().get(0).correction()).isEqualTo("표준오차가 준다");
        assertThat(generator.calls().get(0).evidence()).extracting(e -> e.index()).containsExactly(2);
        Question approved = questions.findBySessionIdAndStatusOrderByPlanPositionAscIdAsc(id, QuestionStatus.APPROVED).get(0);
        assertThat(approved.getHint()).isNotBlank();
        assertThat(approved.getExplanation()).isNotBlank();
    }

    @Test
    void failedGenerationsAreRetriedThenTheSlotIsHeld() throws Exception {
        long id = confirmedSession();
        generator.willRespond(new QuestionGenerationException("timeout"));
        jev.quality.add(QUALITY_FAIL);
        jev.qualityDefault = QUALITY_PASS;

        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION), null);
        awaitStatus(() -> id, LearningSessionStatus.QUESTIONS_READY);

        List<Question> all = questions.findBySessionIdOrderByIdAsc(id);
        assertThat(all).extracting(Question::getStatus).containsExactly(QuestionStatus.REJECTED, QuestionStatus.APPROVED);
        assertThat(all).extracting(Question::getAttempt).containsExactly(2, 3);
        assertThat(generator.targets().get(2).avoidStems()).as("떨어진 문제와 다르게 다시 만든다").contains(all.get(0).getStem());

        long held = confirmedSession();
        jev.qualityDefault = QUALITY_FAIL;
        goals.choose(held, List.of(LearningGoal.CORRECT_MISCONCEPTION), null);
        JsonNode body = awaitFailed(held);

        assertThat(sessions.findById(held).orElseThrow().getStatus()).as("승인 0개면 문제 준비로 넘기지 않음")
                .isEqualTo(LearningSessionStatus.CONFIRMED);
        assertThat(body.get("generating").asBoolean()).isFalse();
        assertThat(body.get("failureReason").asString()).contains("품질 검사");
        assertThat(body.get("questions")).isEmpty();
        assertThat(body.get("held")).singleElement().satisfies(slot -> assertThat(slot.get("type").asString()).isEqualTo("ERROR_FINDING"));
        assertThat(questions.findBySessionIdOrderByIdAsc(held)).hasSize(3).allMatch(q -> q.getStatus() == QuestionStatus.REJECTED);
    }

    @Test
    void whenEveryGenerationFailsTheSessionStaysConfirmedAndChoosingAgainRetries() throws Exception {
        long id = confirmedSession();
        generator.willRespond(new QuestionGenerationException("LLM 호출 실패"), new QuestionGenerationException("LLM 호출 실패"),
                new QuestionGenerationException("LLM 호출 실패"));

        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION), null);
        JsonNode failed = awaitFailed(id);

        assertThat(sessions.findById(id).orElseThrow().getStatus()).isEqualTo(LearningSessionStatus.CONFIRMED);
        assertThat(failed.get("generating").asBoolean()).isFalse();
        assertThat(failed.get("failureReason").asString()).contains("다시 고르면");
        assertThat(questions.findBySessionIdOrderByIdAsc(id)).isEmpty();

        // 목표를 다시 고르면 막히지 않고 다시 만든다
        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION), null);
        awaitStatus(() -> id, LearningSessionStatus.QUESTIONS_READY);
        JsonNode ready = firstStudy(id);
        assertThat(ready.get("failed").asBoolean()).isFalse();
        assertThat(ready.get("failureReason").isNull()).isTrue();
        assertThat(ready.get("questions")).hasSize(1);
    }

    @Test
    void choosingGoalsAgainStopsTheEarlierGenerationAndKeepsOnlyTheNewPlan() throws Exception {
        long id = confirmedSession();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        jev.hold.set(release);

        // 첫 생성은 품질 검사에서 멈춰 있다. 그 사이 목표를 다시 고르면 새 계획으로 다시 만든다.
        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION), null);
        assertThat(jev.holding.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.CONDITION), null);
        awaitStatus(() -> id, LearningSessionStatus.QUESTIONS_READY);

        // 첫 생성이 검사를 마쳐도 지난 계획의 문제는 승인하지 않고, 더 만들지도 않는다.
        release.countDown();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (questions.findBySessionIdOrderByIdAsc(id).stream().anyMatch(q -> q.getStatus() == QuestionStatus.CANDIDATE)) {
            assertThat(Instant.now()).as("첫 생성 마무리 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
        Thread.sleep(300);

        List<Question> all = questions.findBySessionIdOrderByIdAsc(id);
        assertThat(all.getFirst().getStatus()).isEqualTo(QuestionStatus.REJECTED);
        assertThat(all.getFirst().getQualityNote()).contains("학습 목표를 다시 골라");
        assertThat(all).filteredOn(q -> q.getStatus() == QuestionStatus.APPROVED).as("새 계획의 문제만").hasSize(2);
        assertThat(generator.calls()).as("지난 생성은 다시 요청하지 않음").hasSize(2);
        JsonNode body = firstStudy(id);
        assertThat(body.get("planned").asInt()).isEqualTo(2);
        assertThat(body.get("questions")).hasSize(2);
        assertThat(body.get("held")).isEmpty();
    }

    private JsonNode awaitFailed(long sessionId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (true) {
            JsonNode body = firstStudy(sessionId);
            if (body.get("failed").asBoolean()) {
                return body;
            }
            assertThat(Instant.now()).as("생성 실패 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
    }

    @Test
    void onlyTheTargetsThatFailedAreRequestedAgain() {
        long id = confirmedSession();
        generator.willRespond(new UnitQuestionResult(List.of(
                UnitQuestionResult.TargetResult.failed("p0", "형식 오류"),
                UnitQuestionResult.TargetResult.made("p1", new GeneratedQuestion("조건 문제?", List.of(), null,
                        List.of("기준"), "모범 답안", "힌트", "설명", List.of(2))))));

        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.CONDITION), null);
        awaitStatus(() -> id, LearningSessionStatus.QUESTIONS_READY);

        assertThat(generator.calls()).hasSize(2);
        assertThat(generator.calls().get(1).targets()).extracting(t -> t.targetId()).containsExactly("p0");
        assertThat(questions.findBySessionIdAndStatusOrderByPlanPositionAscIdAsc(id, QuestionStatus.APPROVED)).hasSize(2);
    }

    @Test
    void variantsShareTheItemAndRecheckRetiresAmbiguousQuestions() {
        long id = confirmedSession();
        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION), null);
        awaitStatus(() -> id, LearningSessionStatus.QUESTIONS_READY);
        Question original = questions.findBySessionIdAndStatusOrderByPlanPositionAscIdAsc(id, QuestionStatus.APPROVED).get(0);

        Question variant = generation.requestVariant(original.getId()).orElseThrow();
        assertThat(variant.getMemoryItemId()).isEqualTo(original.getMemoryItemId());
        assertThat(variant.getType()).isEqualTo(QuestionType.ERROR_FINDING);
        assertThat(variant.getVariantOfId()).isEqualTo(original.getId());
        assertThat(variant.getPlanPosition()).isNull();
        assertThat(generator.calls().getLast().targets()).singleElement()
                .satisfies(target -> assertThat(target.avoidStems()).contains(original.getStem()));

        jev.quality.add(QUALITY_FAIL);
        Question replacement = generation.recheck(original.getId()).orElseThrow();

        assertThat(questions.findById(original.getId()).orElseThrow().getStatus()).isEqualTo(QuestionStatus.RETIRED);
        assertThat(replacement.getStatus()).isEqualTo(QuestionStatus.APPROVED);
        assertThat(replacement.getVariantOfId()).isEqualTo(original.getId());
    }
}
