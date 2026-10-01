package com.khack.review.question.application;

import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.port.out.FakeLlmPort;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class QuestionGenerationServiceIT {

    @TestConfiguration
    static class FakeLlm {

        @Bean
        @Primary
        FakeLlmPort fakeLlmPort() {
            return new FakeLlmPort();
        }
    }

    /** 단위 0: 사실 3개 + 헷갈린 지점 1개. 단위 1: 사실·경고·실무 팁 1개씩. 6번은 meta 발화. */
    static final SessionInput INPUT = new SessionInput(
            List.of(turn(1, "ETag가 뭐야?"),
                    turn(2, "요청마다 파일을 전부 비교해서 만드는 거지?", Intent.understanding_check, AiVerdict.corrected,
                            "요청마다 비교하지 않고 ETag 문자열만 비교한다"),
                    turn(3, "쉽게 설명해줘"),
                    turn(4, "같으면 304를 주는 거지?", Intent.restatement, AiVerdict.confirmed, null),
                    turn(5, "304가 항상 빠른 근거는?"),
                    turn(6, "복습에 넣어줘", Intent.meta, null, null)),
            List.of(new ReviewUnit("ETag와 조건부 요청",
                            List.of(new KeyPoint("ETag는 리소스 버전 식별자다", List.of(1, 3), null),
                                    new KeyPoint("같으면 본문 없이 304를 응답한다", List.of(1, 3, 4), null),
                                    new KeyPoint("서버는 ETag 문자열만 비교한다", List.of(2), null)),
                            List.of(new ConfusionPoint(2, "요청마다 파일 내용을 전부 비교해서 만든다"))),
                    new ReviewUnit("304 응답의 성능",
                            List.of(new KeyPoint("304도 서버 왕복은 발생한다", List.of(5), null),
                                    new KeyPoint("304가 항상 빠르다고 외우면 안 된다", List.of(5), FactKind.warning),
                                    new KeyPoint("Network 탭에서 304를 확인한다", List.of(5, 6), FactKind.practice)),
                            null)),
            "HTTP 캐시");

    static final List<LearningGoal> GOALS = List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.DISTINGUISH_CONCEPTS,
            LearningGoal.JUDGE_CONDITIONS);

    @Autowired
    QuestionGenerationService service;

    @Autowired
    QuestionRepository questions;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    FakeLlmPort llm;

    static GeneratedQuestions.Item open(String targetId, String question) {
        return new GeneratedQuestions.Item(targetId, question, List.of(), null, List.of("기준 1", "기준 2"), "모범 답안", "힌트", "설명");
    }

    static GeneratedQuestions.Item choice(String targetId, int correct) {
        return new GeneratedQuestions.Item(targetId, "객관식 문제", List.of("가", "나", "다", "라"), correct, List.of("기준"), "답", "힌트", "설명");
    }

    @Test
    void savesCandidatesForConfirmedSessionAndFillsOnlyMissingOnesOnRetry() {
        Long sessionId = saveSession(LearningSessionStatus.CONFIRMED);
        // 단위 1의 조건 판단 문제는 첫 호출과 재요청 모두에서 빠진다.
        llm.willReturn(
                new GeneratedQuestions(List.of(open("t1", "오류 찾기 문제"), choice("t2", 3))),
                new GeneratedQuestions(List.of(choice("t1", 0))),
                new GeneratedQuestions(List.of()));

        QuestionGenerationResult result = service.generate(sessionId, GOALS);

        assertThat(result.candidateIds()).hasSize(3);
        assertThat(result.failures()).singleElement()
                .satisfies(f -> assertThat(f.target().goal()).isEqualTo(LearningGoal.JUDGE_CONDITIONS));
        List<Question> saved = questions.findBySessionIdAndStatusOrderById(sessionId, QuestionStatus.CANDIDATE);
        assertThat(saved).extracting(Question::getId).containsExactlyElementsOf(result.candidateIds());
        assertThat(questions.findBySessionIdAndStatusOrderById(sessionId, QuestionStatus.APPROVED)).isEmpty();

        Question first = saved.get(0);
        assertThat(first.getType()).isEqualTo(QuestionType.ERROR_FINDING);
        assertThat(first.getMemoryItemId()).isEqualTo(itemId(sessionId, MemoryItemKind.CONFUSION));
        assertThat(first.getEvidenceTurns()).containsExactly(2);
        assertThat(first.getAnswerCriteria()).containsExactly("기준 1", "기준 2");
        assertThat(first.getChoices()).isEmpty();
        assertThat(first.getPromptVersion()).isEqualTo(QuestionPrompt.VERSION);
        assertThat(first.getUserId()).isEqualTo(currentUser.id());
        assertThat(saved.get(1).getChoices()).containsExactly("가", "나", "다", "라");
        assertThat(saved.get(1).getCorrectChoiceIndex()).isEqualTo(3);
        assertThat(llm.calls().get(0).userPrompt()).contains("요청마다 비교하지 않고 ETag 문자열만 비교한다");
        // 후보는 품질 검사 전이므로 세션은 아직 문제 준비 상태가 아니다.
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).isEqualTo(LearningSessionStatus.CONFIRMED);

        int callsBefore = llm.calls().size();
        llm.willReturn(new GeneratedQuestions(List.of(open("t1", "조건 판단 문제"))));

        QuestionGenerationResult retry = service.generate(sessionId, GOALS);

        assertThat(llm.calls()).hasSize(callsBefore + 1);
        assertThat(retry.candidateIds()).hasSize(1);
        assertThat(retry.failures()).isEmpty();
        Question added = questions.findById(retry.candidateIds().get(0)).orElseThrow();
        assertThat(added.getType()).isEqualTo(QuestionType.CASE_JUDGMENT);
        assertThat(added.getMemoryItemId()).isEqualTo(itemId(sessionId, MemoryItemKind.WARNING));
        assertThat(questions.findBySessionId(sessionId)).hasSize(4);
    }

    @Test
    void refusesSessionThatUserHasNotConfirmed() {
        Long sessionId = saveSession(LearningSessionStatus.AWAITING_CONFIRMATION);
        int callsBefore = llm.calls().size();

        assertThatThrownBy(() -> service.generate(sessionId, GOALS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWAITING_CONFIRMATION");

        assertThat(llm.calls()).hasSize(callsBefore);
        assertThat(questions.findBySessionId(sessionId)).isEmpty();
    }

    private Long saveSession(LearningSessionStatus status) {
        return transaction.execute(tx -> {
            Long userId = currentUser.id();
            LearningConversation conversation = conversations.save(
                    LearningConversation.fromConnector(userId, INPUT, List.of(), Instant.now()));
            LearningSession session = LearningSession.create(userId, conversation.getId(), INPUT, Instant.now());
            session.moveTo(LearningSessionStatus.REVIEWING);
            session.moveTo(LearningSessionStatus.AWAITING_CONFIRMATION);
            if (status == LearningSessionStatus.CONFIRMED) {
                session.moveTo(LearningSessionStatus.CONFIRMED);
            }
            return sessions.save(session).getId();
        });
    }

    private Long itemId(Long sessionId, MemoryItemKind kind) {
        return transaction.execute(tx -> sessions.findById(sessionId).orElseThrow().items().stream()
                .filter(item -> item.getKind() == kind)
                .map(MemoryItem::getId)
                .findFirst()
                .orElseThrow());
    }
}
