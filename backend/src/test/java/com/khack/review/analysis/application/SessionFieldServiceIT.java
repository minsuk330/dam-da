package com.khack.review.analysis.application;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.FieldSource;
import com.khack.review.analysis.domain.LearningSessionFieldClassified;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.SessionField;
import com.khack.review.analysis.domain.SessionFieldRepository;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

/** 커넥터 저장 → 커밋 뒤 비동기 분야 판정. 사용자가 고른 분야는 다시 판정해도 그대로다. 트랜잭션 커밋이 필요해 테스트 트랜잭션을 쓰지 않는다. */
@SpringBootTest(properties = {"review.analysis.field.auto-classify=true", "review.analysis.field.retry-delay=1ms"})
class SessionFieldServiceIT {

    @TestConfiguration
    static class Config {

        /** 복습 단위 검수도 같은 이벤트로 Jev를 부르므로, 어느 호출에든 맞는 답을 한 결과에 담는다. */
        @Bean
        @Primary
        FakeJevPort fakeJevPort() {
            return new FakeJevPort().willReturn(new JevResult("fake", Map.of(
                    SessionFieldQuestions.FIELD, new JevAnswer.Choice("cs", Map.of(), 0.9),
                    SessionFieldQuestions.SUBFIELD, new JevAnswer.Choice("cs.db", Map.of(), 0.9),
                    UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(0.9),
                    UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(2.0, Map.of(), Map.of(), 0.9))));
        }

        @Bean
        ClassifiedEvents classifiedEvents() {
            return new ClassifiedEvents();
        }
    }

    static class ClassifiedEvents {

        final List<LearningSessionFieldClassified> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(LearningSessionFieldClassified event) {
            received.add(event);
        }
    }

    static final SessionInput INPUT = new SessionInput(
            List.of(turn(1, "SELECT도 락 걸어?")),
            List.of(new com.khack.review.collection.domain.ReviewUnit("MVCC 읽기", List.of(point("일반 SELECT는 락이 없다", 1)), null)),
            "InnoDB");

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    SessionFieldRepository fields;

    @Autowired
    SessionFieldService service;

    @Autowired
    ClassifiedEvents classified;

    @Autowired
    SessionFieldBackfill backfill;

    @Test
    void classifiesAfterCommitAndKeepsTheUsersChoice() {
        Long sessionId = saveAndAwaitField();

        SessionField field = fields.findById(sessionId).orElseThrow();
        assertThat(field.getCode()).isEqualTo("cs.db");
        assertThat(field.getSource()).isEqualTo(FieldSource.AUTO);
        assertThat(field.getReason()).contains("cs.db");
        assertThat(classified.received).contains(new LearningSessionFieldClassified(sessionId, "cs.db"));

        LearningSessionDetail detail = service.choose(sessionId, "cs.os");
        assertThat(detail.field()).isNotNull();
        assertThat(detail.field().label()).isEqualTo("운영체제");
        assertThat(detail.field().fieldLabel()).isEqualTo("컴퓨터·IT");
        assertThat(detail.field().source()).isEqualTo(FieldSource.USER);

        service.classify(sessionId);

        assertThat(fields.findById(sessionId).orElseThrow().getCode()).as("규칙 19").isEqualTo("cs.os");
    }

    @Test
    void backfillLabelsSessionsSavedWithoutAField() {
        Long sessionId = saveAndAwaitField();
        fields.deleteById(sessionId);

        assertThat(sessions.findIdsWithoutField()).contains(sessionId);
        backfill.classifyUnlabeled();

        awaitField(sessionId);
        assertThat(fields.findById(sessionId)).map(SessionField::getCode).hasValue("cs.db");
        assertThat(sessions.findIdsWithoutField()).doesNotContain(sessionId);
    }

    private Long saveAndAwaitField() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        Long sessionId = sessions.findByConversationId(conversationId).orElseThrow().getId();
        awaitField(sessionId);
        return sessionId;
    }

    private void awaitField(Long sessionId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (fields.findById(sessionId).isEmpty()) {
            assertThat(Instant.now()).as("분야 판정 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
    }
}
