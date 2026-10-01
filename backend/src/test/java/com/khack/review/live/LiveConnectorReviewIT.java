package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.ReviewUnit;
import com.khack.review.analysis.domain.ReviewUnitVerdict;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 Jev로 복습 단위를 검수한다: 커넥터 저장 → 커밋 뒤 비동기 검수 → 확인 대기. 판정이 UNAVAILABLE이 아니어야 한다. */
@Tag("live")
@SpringBootTest
class LiveConnectorReviewIT {

    static final SessionInput INPUT = new SessionInput(
            List.of(new UserTurn(1, "InnoDB에서 일반 SELECT도 락을 걸어?", null, Intent.info_request, null, null),
                    new UserTurn(2, "그럼 FOR UPDATE는 스캔 다 하고 나서 락 거는 거 아니야?", null, Intent.understanding_check,
                            AiVerdict.corrected, "스캔하면서 읽는 레코드마다 즉시 잠근다"),
                    new UserTurn(3, "복습에 넣어줘", null, Intent.meta, null, null)),
            List.of(new com.khack.review.collection.domain.ReviewUnit("일관된 읽기와 잠금 읽기",
                    List.of(new KeyPoint("일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다", List.of(1), null),
                            new KeyPoint("SELECT ... FOR UPDATE는 읽는 레코드마다 즉시 배타 락을 건다", List.of(2), null),
                            new KeyPoint("FOR UPDATE를 인덱스 없이 쓰면 스캔한 범위 전체가 잠긴다", List.of(2), FactKind.warning)),
                    List.of(new ConfusionPoint(2, "스캔을 모두 마친 뒤 한꺼번에 락을 건다")))),
            "InnoDB 잠금");

    @Autowired
    Environment env;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    TransactionTemplate transaction;

    @BeforeEach
    void keys() {
        LiveKeys.require(env);
    }

    @Test
    void realJevReviewsTheUnitsAndTheSessionAwaitsConfirmation() {
        SavedSession saved = intake.intake(INPUT);
        Long conversationId = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow().getId();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(120));
        LearningSession session;
        while (true) {
            session = sessions.findByConversationId(conversationId).orElseThrow();
            if (session.getStatus() == LearningSessionStatus.AWAITING_CONFIRMATION) {
                break;
            }
            assertThat(Instant.now()).as("실제 Jev 검수 대기").isBefore(deadline);
            Thread.onSpinWait();
        }

        Long sessionId = session.getId();
        List<ReviewUnit> units = transaction.execute(status -> sessions.findById(sessionId).orElseThrow().getUnits());
        assertThat(units).isNotEmpty().allSatisfy(unit -> {
            assertThat(unit.getVerdict()).as("단위 '%s' 판정", unit.getTitle())
                    .isIn(ReviewUnitVerdict.APPROVED, ReviewUnitVerdict.HELD, ReviewUnitVerdict.REJECTED);
            System.out.printf("[live] 단위 '%s' → %s (%s)%n", unit.getTitle(), unit.getVerdict(), unit.getVerdictReason());
        });
    }
}
