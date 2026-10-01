package com.khack.review.collection.application;

import static com.khack.review.collection.domain.Fixtures.input;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Fidelity;
import com.khack.review.collection.domain.InputPath;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.application.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConversationPersistenceTest {

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    ConversationQueryService query;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    CurrentUser currentUser;

    private SavedSession reload(String sessionId) {
        return query.all().stream().filter(s -> s.id().equals(sessionId)).findFirst().orElseThrow();
    }

    @Test
    void roundTripsV5SessionWithKoreanEmojiCodeAndNewlines() {
        String text = "TCP 핸드쉐잌 🤝\n```js\nconst a = \"x\";\n```";
        UserTurn first = new UserTurn(1, text, "클러스터 인덱스를", Intent.understanding_check, AiVerdict.corrected, "읽는 순간 잠근다");
        UserTurn second = new UserTurn(2, "trx_id가 뭔데", null, Intent.info_request, null, null);
        ReviewUnit unit = new ReviewUnit("잠금 순서",
                List.of(new KeyPoint("레코드마다 즉시 잠근다", List.of(1), null),
                        new KeyPoint("RC에서도 갭 락이 걸린다", List.of(1), FactKind.warning),
                        new KeyPoint("trx_id는 트랜잭션 일련번호", List.of(2), null)),
                List.of(new ConfusionPoint(1, "스캔 후 잠근다")));

        SavedSession saved = intake.intake(new SessionInput(List.of(first, second), List.of(unit), "InnoDB"));

        SavedSession reloaded = reload(saved.id());
        assertThat(reloaded.userTurns()).containsExactly(first, second);
        assertThat(reloaded.reviewUnits()).containsExactly(unit);
        assertThat(reloaded.topicHint()).isEqualTo("InnoDB");
        assertThat(reloaded.source()).isEqualTo("connector");
        assertThat(reloaded.transcription()).isEqualTo("model_transcribed");
    }

    @Test
    void storesInputPathFidelityOwnerAndWarnings() {
        SavedSession saved = intake.intake(input(List.of(turn(1, "인덱스가 뭐야"), turn(2, "그럼 이건?"))));

        LearningConversation stored = conversations.findAll().stream()
                .filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow();
        assertThat(stored.getInputPath()).isEqualTo(InputPath.connector);
        assertThat(stored.getFidelity()).isEqualTo(Fidelity.model_transcribed);
        assertThat(stored.getUserId()).isEqualTo(currentUser.id());
        assertThat(reload(saved.id()).warnings()).isEqualTo(saved.warnings());
    }

    @Test
    void listsOldestFirstSoTheLastIsTheLatest() {
        intake.intake(input(List.of(turn(1, "first"))));
        SavedSession second = intake.intake(input(List.of(turn(1, "second"))));

        List<SavedSession> all = query.all();
        assertThat(all.get(all.size() - 1).id()).isEqualTo(second.id());
    }
}
