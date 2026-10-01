package com.khack.review.memory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.application.LearningSessionDetail;
import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.application.SessionConfirmationService;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.memory.domain.InitialMemoryStateSeeded;
import com.khack.review.memory.domain.MemoryState;
import com.khack.review.memory.domain.MemoryStateRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** 확인 완료 → 같은 트랜잭션에서 초기 평가 → FSRS 초기 상태 (도메인 스토리 S1-9). */
@SpringBootTest
@Import(InitialMemorySeederIT.Events.class)
class InitialMemorySeederIT {

    @TestConfiguration
    static class Events {

        @Bean
        Seeded seeded() {
            return new Seeded();
        }
    }

    static class Seeded {

        final List<InitialMemoryStateSeeded> received = new CopyOnWriteArrayList<>();

        @EventListener
        void on(InitialMemoryStateSeeded event) {
            received.add(event);
        }
    }

    /** 1 질문만 / 2 교정받음 / 3 확인 질문이 맞음 / 4 저장 요청(meta). */
    static final SessionInput INPUT = new SessionInput(
            List.of(new UserTurn(1, "MVCC가 뭐야?", null, Intent.info_request, null, null),
                    new UserTurn(2, "SELECT도 S 락 거는 거 맞지?", null, Intent.understanding_check, AiVerdict.corrected, "일반 SELECT는 락이 없다"),
                    new UserTurn(3, "그러니까 스냅샷을 읽는 거지?", null, Intent.understanding_check, AiVerdict.confirmed, null),
                    new UserTurn(4, "복습에 넣어줘", null, Intent.meta, null, null)),
            List.of(new ReviewUnit("MVCC 읽기", List.of(
                            new KeyPoint("MVCC는 행의 여러 버전을 둔다", List.of(1), null),
                            new KeyPoint("일반 SELECT는 스냅샷을 읽는다", List.of(1, 3), null)),
                            List.of(new ConfusionPoint(2, "일반 SELECT도 S 락을 건다"))),
                    new ReviewUnit("잠금 읽기", List.of(new KeyPoint("FOR UPDATE는 배타 락", List.of(2), null)), null)),
            "InnoDB");

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    LearningSessionQueryService query;

    @Autowired
    SessionConfirmationService confirmation;

    @Autowired
    InitialMemorySeeder seeder;

    @Autowired
    MemoryStateRepository states;

    @Autowired
    Seeded seeded;

    @BeforeEach
    void reset() {
        seeded.received.clear();
    }

    @Test
    void confirmingSeedsItemsFromConversationSignals() {
        long sessionId = savedAndReviewed();
        LearningSessionDetail detail = query.detail(sessionId);
        Long plainFact = detail.units().get(0).items().get(0).id();
        Long checkedFact = detail.units().get(0).items().get(1).id();
        Long confusion = detail.units().get(0).items().get(2).id();
        Long lockFact = detail.units().get(1).items().get(0).id();
        confirmation.setUnitExcluded(sessionId, detail.units().get(1).id(), true);

        confirmation.confirm(sessionId);

        assertThat(seeded.received).singleElement().satisfies(event -> {
            assertThat(event.sessionId()).isEqualTo(sessionId);
            assertThat(event.again()).containsExactly(confusion);
            assertThat(event.good()).containsExactly(checkedFact);
            assertThat(event.unrated()).containsExactly(plainFact);
        });
        Instant conversationAt = detail.createdAt();
        MemoryState again = states.findByMemoryItemId(confusion).orElseThrow();
        MemoryState good = states.findByMemoryItemId(checkedFact).orElseThrow();
        assertThat(again.getLastReview()).isEqualTo(conversationAt);
        assertThat(good.getLastReview()).isEqualTo(conversationAt);
        assertThat(again.getDue()).isBefore(good.getDue());
        assertThat(states.findByMemoryItemId(plainFact)).isEmpty();
        assertThat(states.findByMemoryItemId(lockFact)).as("제외된 항목").isEmpty();
    }

    @Test
    void seedingRunsOnceAndOnlyAfterConfirmation() {
        long sessionId = savedAndReviewed();
        assertThatThrownBy(() -> seeder.seed(sessionId)).isInstanceOf(IllegalStateException.class);

        confirmation.confirm(sessionId);
        Long confusion = query.detail(sessionId).units().get(0).items().get(2).id();
        Instant firstReview = states.findByMemoryItemId(confusion).orElseThrow().getLastReview();

        InitialMemoryStateSeeded again = seeder.seed(sessionId);

        assertThat(again.again()).isEmpty();
        assertThat(again.good()).isEmpty();
        assertThat(states.findByMemoryItemId(confusion).orElseThrow().getLastReview()).isEqualTo(firstReview);
    }

    private long savedAndReviewed() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            var session = sessions.findByConversationId(conversationId).orElseThrow();
            if (session.getStatus() == LearningSessionStatus.AWAITING_CONFIRMATION) {
                return session.getId();
            }
            assertThat(Instant.now()).as("검수 완료 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
    }
}
