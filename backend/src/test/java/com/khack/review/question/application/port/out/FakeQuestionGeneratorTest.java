package com.khack.review.question.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeQuestionGeneratorTest {

    static QuestionRequest request(QuestionType type, List<String> avoid) {
        return new QuestionRequest(11L, MemoryItemKind.CONFUSION, "표본이 크면 표준편차가 준다", "표준오차가 준다", "표준오차", "경영통계",
                List.of(new QuestionRequest.EvidenceTurn(2, "그럼 표본이 크면 표준편차도 줄어?", "corrected", "표준오차가 준다")),
                "CORRECT_MISCONCEPTION", type, null, avoid);
    }

    @Test
    void defaultQuestionsMatchTheRequestedType() {
        FakeQuestionGenerator generator = new FakeQuestionGenerator();

        GeneratedQuestion errorFinding = generator.generate(request(QuestionType.ERROR_FINDING, List.of()));
        GeneratedQuestion choice = generator.generate(request(QuestionType.MULTIPLE_CHOICE, List.of("이전 문제")));

        assertThat(errorFinding.choices()).isEmpty();
        assertThat(errorFinding.correctChoice()).isNull();
        assertThat(errorFinding.answerCriteria()).containsExactly("표준오차가 준다");
        assertThat(errorFinding.evidenceTurns()).containsExactly(2);
        assertThat(choice.choices()).hasSize(3);
        assertThat(choice.correctChoice()).isZero();
        assertThat(choice.stem()).contains("변형 1");
        assertThat(generator.calls()).hasSize(2);
    }

    @Test
    void scriptedResponsesComeFirst() {
        FakeQuestionGenerator generator = new FakeQuestionGenerator()
                .willRespond(new QuestionGenerationException("timeout"));

        assertThatThrownBy(() -> generator.generate(request(QuestionType.ESSAY, List.of())))
                .isInstanceOf(QuestionGenerationException.class);
        assertThat(generator.generate(request(QuestionType.ESSAY, List.of())).stem()).startsWith("[ESSAY]");
    }
}
