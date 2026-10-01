package com.khack.review.question.adapter.out.llm;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.common.application.port.out.LlmPort;
import com.khack.review.question.application.port.out.GeneratedQuestion;
import com.khack.review.question.application.port.out.QuestionGenerationException;
import com.khack.review.question.application.port.out.QuestionGenerator;
import com.khack.review.question.application.port.out.UnitQuestionRequest;
import com.khack.review.question.application.port.out.UnitQuestionResult;
import com.khack.review.question.domain.QuestionType;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LLM으로 복습 단위 1개의 문제들을 만든다(스펙 §6.1, §7.8). 단위당 한 번 호출하고, 빠지거나 형식이 틀린 대상만 한 번 더 요청한다.
 * 여기서는 형식만 확인한다. 근거성·명확성·중복은 서버의 품질 검사(Jev)가 본다.
 * {@code ./gradlew test}에서는 실제 LLM을 부르지 않도록 빈을 만들지 않고, live 테스트에서만 만든다.
 */
@Component
@Fallback
@Profile("!test | live")
public class LlmQuestionGenerator implements QuestionGenerator {

    static final int CHOICE_COUNT = 4;
    static final int MAX_CRITERIA = 3;
    static final int MAX_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(LlmQuestionGenerator.class);

    private final LlmPort llm;

    public LlmQuestionGenerator(LlmPort llm) {
        this.llm = llm;
    }

    @Override
    public UnitQuestionResult generate(UnitQuestionRequest request) {
        Map<String, GeneratedQuestion> made = new LinkedHashMap<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        List<UnitQuestionRequest.Target> pending = request.targets().stream()
                .filter(target -> answerable(target, reasons))
                .toList();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && !pending.isEmpty(); attempt++) {
            GeneratedQuestions generated;
            try {
                generated = llm.generate(QuestionPrompt.SYSTEM, QuestionPrompt.user(request, pending), GeneratedQuestions.class);
            } catch (RuntimeException e) {
                log.warn("문제 생성 LLM 호출 실패 unit={} ({}/{}): {}", request.unitTitle(), attempt, MAX_ATTEMPTS, e.toString());
                if (made.isEmpty() && attempt == MAX_ATTEMPTS) {
                    throw new QuestionGenerationException("LLM 호출에 실패했습니다: " + e.getMessage(), e);
                }
                pending.forEach(target -> reasons.put(target.targetId(), "LLM 호출 실패: " + e.getMessage()));
                continue;
            }
            collect(pending, generated, made, reasons);
            pending = pending.stream().filter(target -> !made.containsKey(target.targetId())).toList();
        }
        return new UnitQuestionResult(request.targets().stream()
                .map(target -> made.containsKey(target.targetId())
                        ? UnitQuestionResult.TargetResult.made(target.targetId(), made.get(target.targetId()))
                        : UnitQuestionResult.TargetResult.failed(target.targetId(),
                                reasons.getOrDefault(target.targetId(), "LLM이 문제를 만들지 않았습니다.")))
                .toList());
    }

    /** 근거가 없어 정답을 정할 수 없는 대상은 LLM에 보내지 않는다(규칙 2, 8). */
    private static boolean answerable(UnitQuestionRequest.Target target, Map<String, String> reasons) {
        if (target.sourceTurns().isEmpty()) {
            reasons.put(target.targetId(), "근거 발화가 없습니다.");
            return false;
        }
        if (target.itemKind() == MemoryItemKind.CONFUSION && isBlank(target.correction())) {
            reasons.put(target.targetId(), "대화 속 AI 교정이 없어 정답의 근거가 없습니다.");
            return false;
        }
        return true;
    }

    private static void collect(List<UnitQuestionRequest.Target> pending, @Nullable GeneratedQuestions generated,
            Map<String, GeneratedQuestion> made, Map<String, String> reasons) {
        if (generated == null || generated.questions() == null) {
            return;
        }
        Map<String, UnitQuestionRequest.Target> byId = new LinkedHashMap<>();
        pending.forEach(target -> byId.put(target.targetId(), target));
        for (GeneratedQuestions.Item item : generated.questions()) {
            UnitQuestionRequest.Target target = item == null ? null : byId.get(item.targetId());
            if (target == null || made.containsKey(target.targetId())) {
                continue;
            }
            String problem = problem(target.type(), item);
            if (problem == null) {
                made.put(target.targetId(), question(target, item));
                reasons.remove(target.targetId());
            } else {
                reasons.put(target.targetId(), problem);
            }
        }
    }

    /** 형식 오류가 있으면 이유를, 없으면 null을 돌려준다. */
    private static @Nullable String problem(QuestionType type, GeneratedQuestions.Item item) {
        if (isBlank(item.stem())) {
            return "문제 문장이 비어 있습니다.";
        }
        List<String> criteria = item.answerCriteria();
        if (criteria == null || criteria.isEmpty() || criteria.stream().anyMatch(LlmQuestionGenerator::isBlank)) {
            return "정답 기준이 없습니다.";
        }
        if (criteria.size() > MAX_CRITERIA) {
            return "정답 기준이 " + MAX_CRITERIA + "개를 넘습니다.";
        }
        if (isBlank(item.modelAnswer()) || isBlank(item.hint()) || isBlank(item.explanation())) {
            return "모범 답안, 힌트, 설명 중 빈 값이 있습니다.";
        }
        if (type == QuestionType.MULTIPLE_CHOICE) {
            List<String> choices = item.choices();
            if (choices == null || choices.size() != CHOICE_COUNT || choices.stream().anyMatch(LlmQuestionGenerator::isBlank)) {
                return "객관식 보기가 " + CHOICE_COUNT + "개가 아닙니다.";
            }
            if (new HashSet<>(choices.stream().map(String::strip).toList()).size() != CHOICE_COUNT) {
                return "객관식 보기가 중복됩니다.";
            }
            Integer correct = item.correctChoice();
            if (correct == null || correct < 0 || correct >= CHOICE_COUNT) {
                return "객관식 정답 위치가 없습니다.";
            }
        }
        return null;
    }

    /** 근거 발화는 모델이 쓰지 않고 대상 항목의 출처 발화를 그대로 쓴다. */
    private static GeneratedQuestion question(UnitQuestionRequest.Target target, GeneratedQuestions.Item item) {
        boolean multipleChoice = target.type() == QuestionType.MULTIPLE_CHOICE;
        return new GeneratedQuestion(
                item.stem().strip(),
                multipleChoice ? item.choices().stream().map(String::strip).toList() : List.of(),
                multipleChoice ? item.correctChoice() : null,
                item.answerCriteria().stream().map(String::strip).toList(),
                item.modelAnswer().strip(),
                item.hint().strip(),
                item.explanation().strip(),
                List.copyOf(target.sourceTurns()));
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
