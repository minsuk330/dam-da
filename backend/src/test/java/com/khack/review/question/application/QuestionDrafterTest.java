package com.khack.review.question.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeLlmPort;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.QuestionPlanner;
import com.khack.review.question.domain.QuestionSource;
import com.khack.review.question.domain.QuestionTarget;
import com.khack.review.question.domain.QuestionType;
import com.khack.review.question.domain.Sources;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionDrafterTest {

    final QuestionSource source = Sources.etag();
    final FakeLlmPort llm = new FakeLlmPort();
    final QuestionDrafter drafter = new QuestionDrafter(llm);

    static GeneratedQuestions.Item open(String targetId) {
        return new GeneratedQuestions.Item(targetId, " 문제 " + targetId, List.of(), null, List.of("기준"), "모범 답안", "힌트", "설명");
    }

    static GeneratedQuestions.Item choice(String targetId, List<String> choices, Integer correct) {
        return new GeneratedQuestions.Item(targetId, "문제 " + targetId, choices, correct, List.of("기준"), "모범 답안", "힌트", "설명");
    }

    static GeneratedQuestions questions(GeneratedQuestions.Item... items) {
        return new GeneratedQuestions(List.of(items));
    }

    List<QuestionTarget> targets(LearningGoal... goals) {
        return QuestionPlanner.plan(source, List.of(goals)).targets();
    }

    @Test
    void callsOncePerUnitAndKeepsPlannedOrder() {
        List<QuestionTarget> targets = targets(LearningGoal.UNDERSTAND_PRINCIPLE, LearningGoal.CORRECT_MISCONCEPTION);
        llm.willReturn(questions(open("t2"), open("t1")), questions(open("t1")));

        QuestionDrafter.Result result = drafter.draft(source, targets);

        assertThat(llm.calls()).hasSize(2);
        assertThat(result.failures()).isEmpty();
        assertThat(result.drafts()).extracting(QuestionDrafter.Draft::target).containsExactlyElementsOf(targets);
        assertThat(result.drafts().get(0).content().body()).isEqualTo("문제 t1");
    }

    @Test
    void promptCarriesTargetItemAndUtterancesButSystemPromptStaysFixed() {
        List<QuestionTarget> targets = targets(LearningGoal.CORRECT_MISCONCEPTION);
        llm.willReturn(questions(open("t1")));

        drafter.draft(source, targets);

        FakeLlmPort.Call call = llm.calls().get(0);
        assertThat(call.systemPrompt()).isEqualTo(QuestionPrompt.SYSTEM);
        assertThat(call.userPrompt())
                .contains("\"targetId\":\"t1\"", "\"type\":\"ERROR_FINDING\"", "잘못 믿은 내용", "AI의 교정", "발화 2")
                .doesNotContain("복습에 넣어줘");
    }

    @Test
    void retriesOnlyTargetsThatWereMissingOrMalformed() {
        List<QuestionTarget> targets = targets(LearningGoal.REMEMBER_CORE).subList(0, 3);
        GeneratedQuestions.Item noCriteria = new GeneratedQuestions.Item("t2", "문제", List.of(), null, List.of(), "답", "힌트", "설명");
        llm.willReturn(questions(open("t1"), noCriteria), questions(open("t1")));

        QuestionDrafter.Result result = drafter.draft(source, targets);

        assertThat(llm.calls()).hasSize(2);
        assertThat(llm.calls().get(1).userPrompt()).contains("\"targetId\":\"t2\"").doesNotContain("\"targetId\":\"t3\"");
        assertThat(result.drafts()).extracting(QuestionDrafter.Draft::target).containsExactly(targets.get(0), targets.get(1));
        assertThat(result.failures()).singleElement().satisfies(f -> assertThat(f.target()).isEqualTo(targets.get(2)));
    }

    @Test
    void multipleChoiceNeedsFourDistinctChoicesAndACorrectIndex() {
        List<QuestionTarget> targets = targets(LearningGoal.DISTINGUISH_CONCEPTS);
        assertThat(targets).hasSize(2).allSatisfy(t -> assertThat(t.type()).isEqualTo(QuestionType.MULTIPLE_CHOICE));
        llm.willReturn(
                questions(choice("t1", List.of("가", "나", "다"), 0)),
                questions(choice("t1", List.of("가", "나", "다", "가 "), 0)),
                questions(choice("t1", List.of("가", "나", "다", "라"), 4)),
                questions(choice("t1", List.of("가", "나", "다", "라"), 2)));

        QuestionDrafter.Result result = drafter.draft(source, targets);

        assertThat(result.failures()).singleElement().satisfies(f -> {
            assertThat(f.target()).isEqualTo(targets.get(0));
            assertThat(f.reason()).contains("중복");
        });
        assertThat(result.drafts()).singleElement().satisfies(d -> {
            assertThat(d.content().choices()).containsExactly("가", "나", "다", "라");
            assertThat(d.content().correctChoiceIndex()).isEqualTo(2);
        });
    }

    @Test
    void nonChoiceQuestionsDropChoices() {
        List<QuestionTarget> targets = targets(LearningGoal.CORRECT_MISCONCEPTION);
        llm.willReturn(questions(choice("t1", List.of("가", "나"), 1)));

        QuestionDrafter.Result result = drafter.draft(source, targets);

        assertThat(result.drafts()).singleElement().satisfies(d -> {
            assertThat(d.content().choices()).isEmpty();
            assertThat(d.content().correctChoiceIndex()).isNull();
        });
    }

    @Test
    void llmFailureFailsThatUnitOnly() {
        List<QuestionTarget> targets = targets(LearningGoal.UNDERSTAND_PRINCIPLE);
        llm.willReturn(new IllegalStateException("과부하"), new IllegalStateException("과부하"), questions(open("t1")));

        QuestionDrafter.Result result = drafter.draft(source, targets);

        assertThat(result.failures()).singleElement().satisfies(f -> {
            assertThat(f.target().unitIndex()).isZero();
            assertThat(f.reason()).contains("과부하");
        });
        assertThat(result.drafts()).singleElement().satisfies(d -> assertThat(d.target().unitIndex()).isEqualTo(1));
    }
}
