package com.khack.review.question.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.common.application.port.out.FakeLlmPort;
import com.khack.review.question.application.port.out.GeneratedQuestion;
import com.khack.review.question.application.port.out.QuestionGenerationException;
import com.khack.review.question.application.port.out.UnitQuestionRequest;
import com.khack.review.question.application.port.out.UnitQuestionResult;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmQuestionGeneratorTest {

    final FakeLlmPort llm = new FakeLlmPort();
    final LlmQuestionGenerator generator = new LlmQuestionGenerator(llm);

    static UnitQuestionRequest.Target fact(String id, QuestionType type, String goal) {
        return new UnitQuestionRequest.Target(id, 1L, MemoryItemKind.FACT, "ETag는 버전 식별자다", null, List.of(1, 3), type, goal,
                null, List.of());
    }

    static UnitQuestionRequest.Target confusion(String id, String correction) {
        return new UnitQuestionRequest.Target(id, 4L, MemoryItemKind.CONFUSION, "요청마다 파일을 비교한다", correction, List.of(2),
                QuestionType.ERROR_FINDING, "CORRECT_MISCONCEPTION", null, List.of());
    }

    static UnitQuestionRequest request(UnitQuestionRequest.Target... targets) {
        return new UnitQuestionRequest("ETag와 조건부 요청", "HTTP 캐시",
                List.of(new UnitQuestionRequest.KeyPoint(1L, MemoryItemKind.FACT, "ETag는 버전 식별자다"),
                        new UnitQuestionRequest.KeyPoint(2L, MemoryItemKind.WARNING, "304가 항상 빠르다고 외우면 안 된다"),
                        new UnitQuestionRequest.KeyPoint(4L, MemoryItemKind.CONFUSION, "요청마다 파일을 비교한다")),
                List.of(new UnitQuestionRequest.EvidenceTurn(1, "ETag가 뭐야?", null, null),
                        new UnitQuestionRequest.EvidenceTurn(2, "요청마다 비교하는 거지?", "corrected", "문자열만 비교한다"),
                        new UnitQuestionRequest.EvidenceTurn(3, "쉽게 설명해줘", null, null)),
                List.of(targets));
    }

    static GeneratedQuestions.Item open(String targetId) {
        return new GeneratedQuestions.Item(targetId, " 문제 " + targetId, List.of(), null, List.of("기준"), "모범 답안", "힌트", "설명");
    }

    static GeneratedQuestions.Item choice(String targetId, List<String> choices, Integer correct) {
        return new GeneratedQuestions.Item(targetId, "문제 " + targetId, choices, correct, List.of("기준"), "모범 답안", "힌트", "설명");
    }

    static GeneratedQuestions questions(GeneratedQuestions.Item... items) {
        return new GeneratedQuestions(List.of(items));
    }

    @Test
    void makesOneQuestionPerTargetInRequestOrderWithEvidenceFromTheItem() {
        llm.willReturn(questions(open("b"), open("a")));

        UnitQuestionResult result = generator.generate(request(confusion("a", "문자열만 비교한다"), fact("b", QuestionType.ESSAY, "PRINCIPLE")));

        assertThat(llm.calls()).hasSize(1);
        assertThat(result.results()).extracting(UnitQuestionResult.TargetResult::targetId).containsExactly("a", "b");
        GeneratedQuestion first = result.results().get(0).question();
        assertThat(first.stem()).isEqualTo("문제 a");
        assertThat(first.evidenceTurns()).containsExactly(2);
        assertThat(first.choices()).isEmpty();
        assertThat(first.correctChoice()).isNull();
        assertThat(result.results().get(1).question().evidenceTurns()).containsExactly(1, 3);
    }

    @Test
    void promptCarriesItemsGoalAndAvoidedStemsAsData() {
        UnitQuestionRequest.Target variant = new UnitQuestionRequest.Target("v", 1L, MemoryItemKind.FACT, "ETag는 버전 식별자다", null,
                List.of(1), QuestionType.MULTIPLE_CHOICE, "DISTINGUISH", "서버는 ETag 문자열만 비교한다", List.of("이미 낸 문제"));
        llm.willReturn(questions(open("a"), choice("v", List.of("가", "나", "다", "라"), 1)));

        generator.generate(request(confusion("a", "문자열만 비교한다"), variant));

        FakeLlmPort.Call call = llm.calls().get(0);
        assertThat(call.systemPrompt()).isEqualTo(QuestionPrompt.SYSTEM);
        assertThat(call.userPrompt())
                .contains("\"targetId\":\"a\"", "\"type\":\"ERROR_FINDING\"", "\"userBelief\":\"요청마다 파일을 비교한다\"",
                        "\"correction\":\"문자열만 비교한다\"", "요청마다 비교하는 거지?")
                .contains("\"compareWith\":\"서버는 ETag 문자열만 비교한다\"", "\"avoidStems\":[\"이미 낸 문제\"]", "\"pointKind\":\"warning\"")
                .doesNotContain("쉽게 설명해줘");
    }

    @Test
    void retriesOnlyMissingOrMalformedTargets() {
        GeneratedQuestions.Item noCriteria = new GeneratedQuestions.Item("b", "문제", List.of(), null, List.of(), "답", "힌트", "설명");
        llm.willReturn(questions(open("a"), noCriteria), questions(open("b")));

        UnitQuestionResult result = generator.generate(request(fact("a", QuestionType.SHORT_ANSWER, "KEY_RECALL"),
                fact("b", QuestionType.ESSAY, null), fact("c", QuestionType.ESSAY, null)));

        assertThat(llm.calls()).hasSize(2);
        assertThat(llm.calls().get(1).userPrompt()).contains("\"targetId\":\"b\"", "\"targetId\":\"c\"").doesNotContain("\"targetId\":\"a\"");
        assertThat(result.results()).extracting(r -> r.question() != null).containsExactly(true, true, false);
        assertThat(result.results().get(2).failure()).isNotBlank();
    }

    @Test
    void multipleChoiceNeedsFourDistinctChoicesAndACorrectIndex() {
        llm.willReturn(questions(choice("a", List.of("가", "나", "다"), 0), choice("b", List.of("가", "나", "다", "라"), 4)),
                questions(choice("a", List.of("가", "나", "다", "가 "), 0), choice("b", List.of(" 가", "나", "다", "라"), 2)));

        UnitQuestionResult result = generator.generate(request(fact("a", QuestionType.MULTIPLE_CHOICE, "DISTINGUISH"),
                fact("b", QuestionType.MULTIPLE_CHOICE, "DISTINGUISH")));

        assertThat(result.results().get(0).question()).isNull();
        assertThat(result.results().get(0).failure()).contains("중복");
        assertThat(result.results().get(1).question().choices()).containsExactly("가", "나", "다", "라");
        assertThat(result.results().get(1).question().correctChoice()).isEqualTo(2);
    }

    @Test
    void confusionWithoutCorrectionFailsWithoutCallingTheModel() {
        UnitQuestionResult result = generator.generate(request(confusion("a", null)));

        assertThat(llm.calls()).isEmpty();
        assertThat(result.results()).singleElement().satisfies(r -> {
            assertThat(r.question()).isNull();
            assertThat(r.failure()).contains("교정");
        });
    }

    @Test
    void throwsWhenTheModelCannotBeReachedAtAll() {
        llm.willReturn(new IllegalStateException("과부하"), new IllegalStateException("과부하"));

        assertThatThrownBy(() -> generator.generate(request(fact("a", QuestionType.ESSAY, "PRINCIPLE"))))
                .isInstanceOf(QuestionGenerationException.class)
                .hasMessageContaining("과부하");
        assertThat(llm.calls()).hasSize(2);
    }

    @Test
    void keepsWhatWasMadeWhenOnlyTheRetryFails() {
        llm.willReturn(questions(open("a")), new IllegalStateException("과부하"));

        UnitQuestionResult result = generator.generate(request(fact("a", QuestionType.ESSAY, "PRINCIPLE"),
                fact("b", QuestionType.SHORT_ANSWER, "KEY_RECALL")));

        assertThat(result.results().get(0).question()).isNotNull();
        assertThat(result.results().get(1).failure()).contains("과부하");
    }
}
