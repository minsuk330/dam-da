package com.khack.review.analysis.application;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.ReviewUnit;
import com.khack.review.analysis.domain.ReviewUnitApproved;
import com.khack.review.analysis.domain.ReviewUnitVerdict;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevCallException;
import com.khack.review.common.application.port.out.JevResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** 커넥터 저장 → 커밋 뒤 비동기 검수 → 확인 대기까지. 트랜잭션 커밋이 필요해 테스트 트랜잭션을 쓰지 않는다. */
@SpringBootTest(properties = "review.analysis.retry-delay=1ms")
class UnitReviewServiceIT {

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        FakeJevPort fakeJevPort() {
            return new FakeJevPort();
        }

        @Bean
        ApprovedEvents approvedEvents() {
            return new ApprovedEvents();
        }
    }

    static class ApprovedEvents {

        final List<ReviewUnitApproved> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(ReviewUnitApproved event) {
            received.add(event);
        }
    }

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    FakeJevPort jev;

    @Autowired
    ApprovedEvents approved;

    @Autowired
    TransactionTemplate transaction;

    static final SessionInput INPUT = new SessionInput(
            List.of(turn(1, "SELECT도 락 걸어?"), turn(2, "FOR UPDATE는?")),
            List.of(new com.khack.review.collection.domain.ReviewUnit("MVCC 읽기", List.of(point("일반 SELECT는 락이 없다", 1)), null),
                    new com.khack.review.collection.domain.ReviewUnit("잠금 읽기", List.of(point("FOR UPDATE는 배타 락", 2)), null)),
            "InnoDB");

    static JevResult result(double worth, double fit, double confidence) {
        return new JevResult("fake", Map.of(
                UnitReviewQuestions.WORTH_REVIEWING, new JevAnswer.Noul(worth),
                UnitReviewQuestions.EVIDENCE_FIT, new JevAnswer.Score(fit, Map.of(), Map.of(), confidence)));
    }

    @BeforeEach
    void reset() {
        approved.received.clear();
    }

    @Test
    void reviewRunsAfterCommitAndAwaitsConfirmation() {
        jev.willRespond(result(0.9, 2.0, 0.9), result(0.55, 2.0, 0.9));

        Long sessionId = saveAndAwaitReview();

        List<ReviewUnit> units = units(sessionId);
        assertThat(units).extracting(ReviewUnit::getVerdict)
                .containsExactly(ReviewUnitVerdict.APPROVED, ReviewUnitVerdict.HELD);
        assertThat(units).noneMatch(ReviewUnit::isExcluded);
        assertThat(approved.received).containsExactly(new ReviewUnitApproved(sessionId, units.get(0).getId()));
        assertThat(jev.calls().getLast().state()).isInstanceOfSatisfying(UnitReviewState.class, state -> {
            assertThat(state.unitTitle()).isEqualTo("잠금 읽기");
            assertThat(state.evidence()).extracting(UnitReviewState.EvidenceTurn::index).containsExactly(2);
        });
    }

    @Test
    void jevFailureStillAwaitsConfirmationWithoutApproval() {
        jev.willFail(new JevCallException(401, "unauthorized", null));

        Long sessionId = saveAndAwaitReview();

        assertThat(units(sessionId)).extracting(ReviewUnit::getVerdict)
                .containsOnly(ReviewUnitVerdict.UNAVAILABLE);
        assertThat(approved.received).isEmpty();
    }

    private Long saveAndAwaitReview() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            LearningSession session = sessions.findByConversationId(conversationId).orElseThrow();
            if (session.getStatus() == LearningSessionStatus.AWAITING_CONFIRMATION) {
                return session.getId();
            }
            assertThat(Instant.now()).as("검수 완료 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
    }

    private List<ReviewUnit> units(Long sessionId) {
        return transaction.execute(status -> {
            List<ReviewUnit> units = sessions.findById(sessionId).orElseThrow().getUnits();
            units.forEach(unit -> unit.getItems().size());
            return units;
        });
    }
}
