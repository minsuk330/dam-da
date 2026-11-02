package com.khack.review.analysis.application;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.port.out.FakeJevPort;
import com.khack.review.common.application.port.out.JevCallException;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

/** 서버가 검수 도중 멈춰 검수 중으로 남은 세션을 재시작 복구가 다시 검수해 확인 대기로 넘기는지 확인한다(#189). */
@SpringBootTest(properties = "review.analysis.retry-delay=1ms")
class UnitReviewRecoveryIT {

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        FakeJevPort fakeJevPort() {
            return new FakeJevPort();
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
    FakeJevPort jev;

    @Autowired
    UnitReviewRecovery recovery;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transaction;

    @Test
    void sessionLeftReviewingIsReviewedAgainAndAwaitsConfirmation() {
        jev.willFail(new JevCallException(401, "unauthorized", null));
        Long sessionId = awaitStatus(save(), LearningSessionStatus.AWAITING_CONFIRMATION);
        // 검수 도중 서버가 멈춘 상태를 만든다
        transaction.executeWithoutResult(status -> entityManager
                .createQuery("update LearningSession s set s.status = :status where s.id = :id")
                .setParameter("status", LearningSessionStatus.REVIEWING).setParameter("id", sessionId).executeUpdate());

        recovery.resumeInterrupted();

        assertThat(awaitStatus(sessionId, LearningSessionStatus.AWAITING_CONFIRMATION)).isEqualTo(sessionId);
    }

    private Long save() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (sessions.findByConversationId(conversationId).isEmpty()) {
            assertThat(Instant.now()).as("세션 생성 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
        return sessions.findByConversationId(conversationId).orElseThrow().getId();
    }

    private Long awaitStatus(Long sessionId, LearningSessionStatus expected) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            LearningSession session = sessions.findById(sessionId).orElseThrow();
            if (session.getStatus() == expected) {
                return session.getId();
            }
            assertThat(Instant.now()).as("%s 대기", expected).isBefore(deadline);
            Thread.onSpinWait();
        }
    }
}
