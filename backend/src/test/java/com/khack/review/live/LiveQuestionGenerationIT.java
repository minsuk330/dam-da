package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.application.SessionConfirmationService;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.practice.application.LearningGoalService;
import com.khack.review.practice.domain.LearningGoal;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * 실제 OpenAI로 문제를 만들고 실제 Jev로 품질을 검사한다: 커넥터 저장 → 검수 → 확인 → 학습 목표 → 문제 생성 → 문제 준비.
 * 만든 문제는 형식을 갖춰야 하고, 승인된 문제가 하나 이상 있어야 한다.
 */
@Tag("live")
@SpringBootTest
class LiveQuestionGenerationIT {

    @Autowired
    Environment env;

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
    QuestionRepository questions;

    @BeforeEach
    void keys() {
        LiveKeys.require(env);
    }

    @Test
    void realModelGeneratesQuestionsAndRealJevApprovesSome() {
        SavedSession saved = intake.intake(LiveConnectorReviewIT.INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        long id = await(() -> sessions.findByConversationId(conversationId).orElseThrow().getId(),
                LearningSessionStatus.AWAITING_CONFIRMATION, Duration.ofSeconds(120));
        confirmation.confirm(id);

        goals.choose(id, List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.PRINCIPLE, LearningGoal.CONDITION), null);
        await(() -> id, LearningSessionStatus.QUESTIONS_READY, Duration.ofSeconds(600));

        List<Question> made = questions.findBySessionIdOrderByIdAsc(id);
        made.forEach(q -> System.out.printf("[live] %s %s (시도 %d) → %s | %s%n        %s%n        기준: %s%n", q.getLearningGoal(),
                q.getType(), q.getAttempt(), q.getStatus(), q.getQualityNote(), q.getStem(), q.getAnswerCriteria()));
        assertThat(made).as("실제 생성기가 만든 문제").isNotEmpty().allSatisfy(q -> {
            assertThat(q.getStem()).isNotBlank();
            assertThat(q.getAnswerCriteria()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
            assertThat(q.getHint()).isNotBlank();
            assertThat(q.getExplanation()).isNotBlank();
            assertThat(q.getEvidenceTurns()).isNotEmpty();
            if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
                assertThat(q.getChoices()).hasSize(4);
            }
        });
        assertThat(made).as("품질 검사를 통과한 문제").anyMatch(q -> q.getStatus() == QuestionStatus.APPROVED);
    }

    private long await(Supplier<Long> sessionId, LearningSessionStatus status, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            Long id = sessionId.get();
            if (sessions.findById(id).orElseThrow().getStatus() == status) {
                return id;
            }
            assertThat(Instant.now()).as("%s 대기", status).isBefore(deadline);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
