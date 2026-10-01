package com.khack.review.analysis.application;

import static com.khack.review.collection.domain.Fixtures.input;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
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
}
