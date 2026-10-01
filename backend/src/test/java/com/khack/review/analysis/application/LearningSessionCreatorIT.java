package com.khack.review.analysis.application;

import static com.khack.review.collection.domain.Fixtures.input;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LearningSessionCreatorIT {

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Test
    void savingAConversationCreatesItsLearningSession() {
        SavedSession saved = intake.intake(input(List.of(turn(1, "인덱스가 뭐야"))));
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();

        LearningSession session = sessions.findByConversationId(conversationId).orElseThrow();

        assertThat(session.getStatus()).isEqualTo(LearningSessionStatus.RECEIVED);
        assertThat(session.getTopicHint()).isEqualTo("InnoDB");
        assertThat(session.getUnits()).hasSize(1);
        assertThat(session.items()).hasSize(1);
    }

    @Test
    void missingUserBeliefIsAWarningNotARollback() {
        SessionInput input = new SessionInput(
                List.of(new UserTurn(1, "그럼 표본이 크면 표준편차도 줄어?", null, Intent.understanding_check, AiVerdict.corrected, "표준오차가 준다")),
                List.of(new ReviewUnit("표준오차", List.of(new KeyPoint("표본이 커져도 표준편차는 줄지 않는다", List.of(1), null)),
                        List.of(new ConfusionPoint(1, null)))),
                "경영통계");

        SavedSession saved = intake.intake(input);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();

        assertThat(saved.warnings()).anyMatch(w -> w.contains("userBelief"));
        assertThat(sessions.findByConversationId(conversationId).orElseThrow().items())
                .extracting(item -> item.getKind()).containsExactly(MemoryItemKind.FACT);
    }
}
