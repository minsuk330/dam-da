package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.practice.application.NextActionJudge;
import com.khack.review.practice.application.NextActionState;
import com.khack.review.practice.application.port.out.FeedbackContent;
import com.khack.review.practice.application.port.out.FeedbackContentGenerator;
import com.khack.review.practice.application.port.out.FeedbackContentRequest;
import com.khack.review.practice.application.port.out.FeedbackContentRequest.EvidenceTurn;
import com.khack.review.practice.domain.AttemptOutcome;
import com.khack.review.practice.domain.FeedbackAction;
import com.khack.review.practice.domain.FeedbackRules;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * 단계적 피드백을 실제 OpenAI와 Jev로 확인한다. 힌트는 정답을 담지 않아야 하고, 개념 설명은 요청에 있던 근거 발화로 이어져야 하며,
 * 처음 틀린 첫 학습 문제의 다음 행동은 Jev가 힌트로 골라야 한다(스펙 §7 5단계).
 */
@Tag("live")
@SpringBootTest
class LiveFeedbackIT {

    static final String STEM = "한 학습자가 \"FOR UPDATE는 스캔을 모두 마친 뒤 한꺼번에 락을 건다\"고 설명했습니다. 틀린 곳을 찾아 바르게 고치세요.";
    static final String BELIEF = "스캔을 모두 마친 뒤 한꺼번에 락을 건다";
    static final String CORRECTION = "스캔을 마친 뒤가 아니라 스캔하면서 읽는 레코드마다 즉시 배타 락을 건다";

    static final FeedbackContentRequest REQUEST = new FeedbackContentRequest(STEM, QuestionType.ERROR_FINDING, List.of(),
            List.of("스캔하면서 읽는 레코드마다 즉시 배타 락을 건다고 고친다"), CORRECTION + ".", MemoryItemKind.CONFUSION, BELIEF, BELIEF,
            CORRECTION,
            List.of(new EvidenceTurn(1, "InnoDB에서 SELECT ... FOR UPDATE는 락을 어떻게 걸어?", null, null),
                    new EvidenceTurn(2, "그럼 FOR UPDATE는 스캔 다 하고 나서 락 거는 거 아니야?", "corrected", CORRECTION)),
            List.of("틀린 곳 없어요. 다 훑고 나서 한 번에 잠그는 게 맞아요."), "락을 거는 시점이 언제인지 떠올려 보세요.");

    @Autowired
    Environment env;

    @Autowired
    FeedbackContentGenerator generator;

    @Autowired
    NextActionJudge nextAction;

    @BeforeEach
    void keys() {
        LiveKeys.require(env);
    }

    @Test
    void hintPointsADirectionWithoutGivingTheAnswer() {
        FeedbackContent hint = generator.hint(REQUEST);

        System.out.println("[live] 힌트 → " + hint.text() + " " + hint.evidenceTurns());
        assertThat(hint.text()).isNotBlank().doesNotContain("레코드마다 즉시");
        assertThat(hint.evidenceTurns()).isSubsetOf(1, 2);
    }

    @Test
    void explanationComparesTheOldBeliefAndLinksToEvidenceFromTheConversation() {
        FeedbackContent explanation = generator.explanation(REQUEST);

        System.out.println("[live] 설명 → " + explanation.text() + " " + explanation.evidenceTurns());
        assertThat(explanation.text()).contains("레코드");
        assertThat(explanation.evidenceTurns()).isNotEmpty().isSubsetOf(1, 2);
    }

    @Test
    void jevGivesAHintFirstWhenAFirstStudyQuestionIsMissedOnce() {
        NextActionState state = new NextActionState(PracticeKind.FIRST_STUDY, false, QuestionType.ERROR_FINDING, STEM,
                AttemptOutcome.WRONG, List.of(new NextActionState.Step(AttemptKind.FIRST_UNASSISTED, AttemptOutcome.WRONG)),
                false, false, false);
        FeedbackRules.Plan plan = FeedbackRules.plan(new FeedbackRules.State(PracticeKind.FIRST_STUDY, false, AttemptOutcome.WRONG,
                false, false, false, false, false, false));

        NextActionJudge.Choice choice = nextAction.choose(state, plan);

        System.out.printf("[live] 다음 행동 → %s (%s, %s)%n", choice.action(), choice.decidedBy(), choice.detail());
        assertThat(plan.allowed()).containsExactlyInAnyOrder(FeedbackAction.GIVE_HINT, FeedbackAction.EXPLAIN_CONCEPT);
        assertThat(choice.action()).isEqualTo(FeedbackAction.GIVE_HINT);
        assertThat(choice.decidedBy()).as("기본 행동으로 물러선 것이 아니라 Jev가 골랐다").isEqualTo(NextActionJudge.DecidedBy.JEV);
    }
}
