package com.khack.review.question.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeQuestionGeneratorTest {

    static UnitQuestionRequest.Target target(String id, QuestionType type, List<String> avoid) {
        return new UnitQuestionRequest.Target(id, 11L, MemoryItemKind.CONFUSION, "표본이 크면 표준편차가 준다", "표준오차가 준다",
                List.of(2), type, "CORRECT_MISCONCEPTION", null, avoid);
    }

    static UnitQuestionRequest request(UnitQuestionRequest.Target... targets) {
        return new UnitQuestionRequest("표준오차", "경영통계",
                List.of(new UnitQuestionRequest.KeyPoint(10L, MemoryItemKind.FACT, "표준오차는 표본평균의 퍼짐"),
                        new UnitQuestionRequest.KeyPoint(11L, MemoryItemKind.CONFUSION, "표본이 크면 표준편차가 준다")),
                List.of(new UnitQuestionRequest.EvidenceTurn(2, "그럼 표본이 크면 표준편차도 줄어?", "corrected", "표준오차가 준다")),
                List.of(targets));
    }

    @Test
    void defaultResultHasOneQuestionPerTargetInTheRequestedType() {
        FakeQuestionGenerator generator = new FakeQuestionGenerator();

        UnitQuestionResult result = generator.generate(request(target("a", QuestionType.ERROR_FINDING, List.of()),
                target("b", QuestionType.MULTIPLE_CHOICE, List.of("이전 문제"))));

        assertThat(result.results()).extracting(UnitQuestionResult.TargetResult::targetId).containsExactly("a", "b");
        GeneratedQuestion errorFinding = result.results().get(0).question();
        assertThat(errorFinding.choices()).isEmpty();
        assertThat(errorFinding.correctChoice()).isNull();
        assertThat(errorFinding.answerCriteria()).containsExactly("표준오차가 준다");
        assertThat(errorFinding.hint()).isNotBlank();
        assertThat(errorFinding.evidenceTurns()).containsExactly(2);
        GeneratedQuestion choice = result.results().get(1).question();
        assertThat(choice.choices()).hasSize(4);
        assertThat(choice.correctChoice()).isZero();
        assertThat(choice.stem()).contains("변형 1");
        assertThat(generator.targets()).hasSize(2);
    }

    @Test
    void scriptedResponsesComeFirst() {
        FakeQuestionGenerator generator = new FakeQuestionGenerator()
                .willRespond(new QuestionGenerationException("timeout"),
                        new UnitQuestionResult(List.of(UnitQuestionResult.TargetResult.failed("a", "형식 오류"))));

        assertThatThrownBy(() -> generator.generate(request(target("a", QuestionType.ESSAY, List.of()))))
                .isInstanceOf(QuestionGenerationException.class);
        assertThat(generator.generate(request(target("a", QuestionType.ESSAY, List.of()))).results().get(0).failure())
                .isEqualTo("형식 오류");
        assertThat(generator.generate(request(target("a", QuestionType.ESSAY, List.of()))).results().get(0).question().stem())
                .startsWith("[ESSAY]");
    }
}
