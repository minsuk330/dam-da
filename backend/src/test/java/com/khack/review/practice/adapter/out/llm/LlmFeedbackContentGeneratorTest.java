package com.khack.review.practice.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.common.application.port.out.FakeLlmPort;
import com.khack.review.practice.adapter.out.llm.LlmFeedbackContentGenerator.Generated;
import com.khack.review.practice.adapter.out.llm.LlmFeedbackContentGenerator.GeneratedPrerequisite;
import com.khack.review.practice.application.port.out.FeedbackContent;
import com.khack.review.practice.application.port.out.FeedbackContentRequest;
import com.khack.review.practice.application.port.out.FeedbackContentRequest.EvidenceTurn;
import com.khack.review.practice.application.port.out.FeedbackGenerationException;
import com.khack.review.practice.application.port.out.PrerequisiteSuggestion;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmFeedbackContentGeneratorTest {

    static final String BELIEF = "스캔을 모두 마친 뒤 한꺼번에 락을 건다";
    static final String CORRECTION = "스캔하면서 읽는 레코드마다 즉시 잠근다";
    static final String MODEL_ANSWER = "스캔하면서 읽는 레코드마다 즉시 배타 락을 건다";

    static final FeedbackContentRequest CONFUSION = new FeedbackContentRequest(
            "다음 설명에서 틀린 곳을 찾아 고치세요: \"FOR UPDATE는 스캔을 마친 뒤 한꺼번에 락을 건다.\"", QuestionType.ERROR_FINDING,
            List.of(), List.of("읽는 레코드마다 즉시 잠근다고 고친다"), MODEL_ANSWER, MemoryItemKind.CONFUSION, BELIEF, BELIEF, CORRECTION,
            List.of(new EvidenceTurn(1, "FOR UPDATE는 어떻게 동작해?", null, null),
                    new EvidenceTurn(2, "스캔 다 하고 나서 락 거는 거 아니야?", "corrected", CORRECTION)),
            List.of("틀린 곳 없다"), "락을 거는 시점을 떠올려 보세요.");

    static final FeedbackContentRequest FACT = new FeedbackContentRequest(
            "InnoDB의 일반 SELECT는 무엇을 읽나요?", QuestionType.SHORT_ANSWER, List.of(), List.of("MVCC 스냅샷을 읽는다"),
            "MVCC 스냅샷", MemoryItemKind.FACT, "일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다", null, null,
            List.of(new EvidenceTurn(1, "SELECT도 락 걸어?", null, null)), List.of(), null);

    final FakeLlmPort llm = new FakeLlmPort();
    final LlmFeedbackContentGenerator generator = new LlmFeedbackContentGenerator(llm);

    @Test
    void hintSendsTheRequestAsDataAndKeepsOnlyEvidenceFromTheRequest() {
        llm.willReturn(new Generated(" 언제 잠그는지, 그 시점을 다시 생각해 보세요. ", List.of(2, 9, 2)));

        FeedbackContent hint = generator.hint(CONFUSION);

        assertThat(hint.text()).isEqualTo("언제 잠그는지, 그 시점을 다시 생각해 보세요.");
        assertThat(hint.evidenceTurns()).as("요청에 없던 9번은 버리고 중복도 없앤다").containsExactly(2);
        FakeLlmPort.Call call = llm.calls().get(0);
        assertThat(call.systemPrompt()).isEqualTo(FeedbackPrompt.HINT);
        assertThat(call.userPrompt())
                .contains("\"kind\":\"confusion\"", "\"userBelief\":\"" + BELIEF + "\"", "\"correction\":\"" + CORRECTION + "\"")
                .contains("\"previousAnswers\":[\"틀린 곳 없다\"]", "\"storedText\":\"락을 거는 시점을 떠올려 보세요.\"", "\"aiVerdict\":\"corrected\"");
    }

    @Test
    void hintMayHaveNoEvidence() {
        llm.willReturn(new Generated("읽기와 잠금이 어떤 관계인지 떠올려 보세요.", null));

        assertThat(generator.hint(FACT).evidenceTurns()).isEmpty();
        assertThat(llm.calls().get(0).userPrompt())
                .contains("\"kind\":\"fact\"", "\"content\":\"일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다\"")
                .doesNotContain("previousAnswers", "storedText", "choices", "userBelief");
    }

    @Test
    void hintThatContainsTheAnswerIsRejectedSoTheStoredHintIsUsed() {
        llm.willReturn(new Generated("정답은 스캔하면서 읽는 레코드마다, 즉시 배타 락을 건다 입니다.", List.of(2)));
        assertThatThrownBy(() -> generator.hint(CONFUSION)).isInstanceOf(FeedbackGenerationException.class)
                .hasMessageContaining("정답");

        llm.willReturn(new Generated("대화에서는 \"" + CORRECTION + "\"고 했어요.", List.of(2)));
        assertThatThrownBy(() -> generator.hint(CONFUSION)).as("교정 문구를 그대로 담아도 정답 노출이다")
                .isInstanceOf(FeedbackGenerationException.class);
    }

    @Test
    void shortAnswerPhrasesAreNotUsedForTheLeakCheck() {
        FeedbackContentRequest shortAnswer = new FeedbackContentRequest("맞으면 O, 틀리면 X", QuestionType.SHORT_ANSWER, List.of(),
                List.of("O"), "O", MemoryItemKind.FACT, "사실", null, null, List.of(new EvidenceTurn(1, "질문", null, null)),
                List.of(), null);
        llm.willReturn(new Generated("O와 X 중 무엇인지 조건을 다시 읽어 보세요.", List.of()));

        assertThat(generator.hint(shortAnswer).text()).contains("조건");
    }

    @Test
    void explanationFallsBackToTheItemsEvidenceWhenTheModelGivesNone() {
        llm.willReturn(new Generated("당시에는 스캔을 마친 뒤 잠근다고 생각했지만, 실제로는 읽는 레코드마다 바로 잠급니다.", List.of(7)));

        FeedbackContent explanation = generator.explanation(CONFUSION);

        assertThat(explanation.evidenceTurns()).as("설명은 원문 근거로 이어져야 한다").containsExactly(1, 2);
        assertThat(explanation.text()).as("설명은 정답을 말해도 된다").contains("바로 잠급니다");
        assertThat(llm.calls().get(0).systemPrompt()).isEqualTo(FeedbackPrompt.EXPLANATION);
    }

    @Test
    void emptyOrOverlongTextIsAGenerationFailure() {
        llm.willReturn(new Generated("  ", List.of(1)));
        assertThatThrownBy(() -> generator.hint(FACT)).isInstanceOf(FeedbackGenerationException.class);

        llm.willReturn(new Generated("가".repeat(LlmFeedbackContentGenerator.MAX_HINT_CHARS + 1), List.of(1)));
        assertThatThrownBy(() -> generator.hint(FACT)).isInstanceOf(FeedbackGenerationException.class).hasMessageContaining("깁니다");

        llm.willReturn(new Generated("가".repeat(LlmFeedbackContentGenerator.MAX_EXPLANATION_CHARS + 1), List.of(1)));
        assertThatThrownBy(() -> generator.explanation(FACT)).isInstanceOf(FeedbackGenerationException.class);
    }

    @Test
    void llmFailureBecomesAGenerationFailureWithoutRetrying() {
        llm.willReturn(new IllegalStateException("과부하"));

        assertThatThrownBy(() -> generator.explanation(FACT)).isInstanceOf(FeedbackGenerationException.class)
                .hasMessageContaining("과부하");
        assertThat(llm.calls()).as("학습자가 기다리는 자리라 다시 요청하지 않는다").hasSize(1);
    }

    @Test
    void prerequisiteNeedsBothAConceptAndAReason() {
        llm.willReturn(new GeneratedPrerequisite(" 잠금 읽기와 일관된 읽기 ", "두 읽기 방식의 차이를 알아야 잠금 시점을 구분할 수 있어요."));
        PrerequisiteSuggestion suggestion = generator.prerequisite(CONFUSION);
        assertThat(suggestion.concept()).isEqualTo("잠금 읽기와 일관된 읽기");
        assertThat(suggestion.reason()).contains("차이");
        assertThat(llm.calls().get(0).systemPrompt()).isEqualTo(FeedbackPrompt.PREREQUISITE);

        llm.willReturn(new GeneratedPrerequisite("개념", null));
        assertThatThrownBy(() -> generator.prerequisite(CONFUSION)).isInstanceOf(FeedbackGenerationException.class);
    }
}
