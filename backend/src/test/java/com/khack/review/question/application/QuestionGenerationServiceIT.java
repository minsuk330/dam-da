package com.khack.review.question.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeLlmPort;
import com.khack.review.question.domain.ItemKind;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionSource;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import com.khack.review.question.domain.Sources;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest
class QuestionGenerationServiceIT {

    @TestConfiguration
    static class FakeLlm {

        @Bean
        @Primary
        FakeLlmPort fakeLlmPort() {
            return new FakeLlmPort();
        }
    }

    @Autowired
    QuestionGenerationService service;

    @Autowired
    QuestionRepository questions;

    @Autowired
    FakeLlmPort llm;

    @Test
    void savesGeneratedQuestionsAsCandidatesWithEvidence() {
        QuestionSource etag = Sources.etag();
        QuestionSource source = new QuestionSource("session-it", etag.turns(), etag.units());
        llm.willReturn(
                new GeneratedQuestions(List.of(
                        new GeneratedQuestions.Item("t1", "오류 찾기 문제", List.of(), null, List.of("기준 1", "기준 2"), "모범 답안", "힌트", "설명"),
                        new GeneratedQuestions.Item("t2", "객관식 문제", List.of("가", "나", "다", "라"), 3, List.of("기준"), "라", "힌트", "설명"))),
                new GeneratedQuestions(List.of(
                        new GeneratedQuestions.Item("t1", "객관식 문제 2", List.of("가", "나", "다", "라"), 0, List.of("기준"), "가", "힌트", "설명"))));

        QuestionGenerationResult result = service.generate(source,
                List.of(LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.DISTINGUISH_CONCEPTS, LearningGoal.JUDGE_CONDITIONS));

        assertThat(result.candidateIds()).hasSize(3);
        assertThat(result.failures()).singleElement()
                .satisfies(f -> assertThat(f.target().goal()).isEqualTo(LearningGoal.JUDGE_CONDITIONS));
        List<Question> saved = questions.findBySessionIdAndStatusOrderById("session-it", QuestionStatus.CANDIDATE);
        assertThat(saved).extracting(Question::getId).containsExactlyElementsOf(result.candidateIds());
        assertThat(questions.findBySessionIdAndStatusOrderById("session-it", QuestionStatus.APPROVED)).isEmpty();

        Question first = saved.get(0);
        assertThat(first.getType()).isEqualTo(QuestionType.ERROR_FINDING);
        assertThat(first.getItemKind()).isEqualTo(ItemKind.CONFUSION_POINT);
        assertThat(first.getEvidenceTurns()).containsExactly(2);
        assertThat(first.getAnswerCriteria()).containsExactly("기준 1", "기준 2");
        assertThat(first.getChoices()).isEmpty();
        assertThat(first.getPromptVersion()).isEqualTo(QuestionPrompt.VERSION);
        assertThat(first.getUserId()).isNotNull();

        Question choice = saved.get(1);
        assertThat(choice.getChoices()).containsExactly("가", "나", "다", "라");
        assertThat(choice.getCorrectChoiceIndex()).isEqualTo(3);
    }
}
