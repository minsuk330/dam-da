package com.khack.review.question.application;

import com.khack.review.common.application.port.out.LlmPort;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionSource;
import com.khack.review.question.domain.QuestionTarget;
import com.khack.review.question.domain.QuestionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 출제 대상마다 LLM으로 문제 내용을 만든다. 복습 단위 하나당 한 번 호출하고, 빠지거나 형식이 틀린 대상만 한 번 더 요청한다.
 * 여기서는 형식만 확인한다. 근거성·명확성 같은 품질 판단은 품질 검사(Jev)의 몫이다.
 */
@Component
public class QuestionDrafter {

    static final int CHOICE_COUNT = 4;
    static final int MAX_CRITERIA = 3;

    private static final Logger log = LoggerFactory.getLogger(QuestionDrafter.class);

    private final LlmPort llm;

    public QuestionDrafter(LlmPort llm) {
        this.llm = llm;
    }

    public record Draft(QuestionTarget target, Question.Content content) {
    }

    public record Failure(QuestionTarget target, String reason) {
    }

    public record Result(List<Draft> drafts, List<Failure> failures) {
    }

    public Result draft(QuestionSource source, List<QuestionTarget> targets) {
        Map<Integer, List<QuestionTarget>> byUnit = targets.stream()
                .collect(Collectors.groupingBy(QuestionTarget::unitIndex, LinkedHashMap::new, Collectors.toList()));
        Map<QuestionTarget, Question.Content> contents = new LinkedHashMap<>();
        Map<QuestionTarget, String> reasons = new LinkedHashMap<>();
        byUnit.forEach((unitIndex, unitTargets) -> {
            List<QuestionTarget> pending = unitTargets;
            for (int attempt = 0; attempt < 2 && !pending.isEmpty(); attempt++) {
                request(source, unitIndex, pending, contents, reasons);
                pending = pending.stream().filter(t -> !contents.containsKey(t)).toList();
            }
        });
        // 계획한 순서(헷갈린 지점 먼저)를 유지한다.
        List<Draft> drafts = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (QuestionTarget target : targets) {
            Question.Content content = contents.get(target);
            if (content != null) {
                drafts.add(new Draft(target, content));
            } else {
                failures.add(new Failure(target, reasons.getOrDefault(target, "LLM이 문제를 만들지 않았습니다.")));
            }
        }
        return new Result(drafts, failures);
    }

    private void request(QuestionSource source, int unitIndex, List<QuestionTarget> targets,
            Map<QuestionTarget, Question.Content> contents, Map<QuestionTarget, String> reasons) {
        Map<String, QuestionTarget> byId = new LinkedHashMap<>();
        List<QuestionPrompt.Identified> identified = new ArrayList<>();
        for (QuestionTarget target : targets) {
            String id = "t" + (identified.size() + 1);
            byId.put(id, target);
            identified.add(new QuestionPrompt.Identified(id, target));
        }
        GeneratedQuestions generated;
        try {
            generated = llm.generate(QuestionPrompt.SYSTEM, QuestionPrompt.user(source, unitIndex, identified),
                    GeneratedQuestions.class);
        } catch (RuntimeException e) {
            log.warn("문제 생성 LLM 호출 실패 session={} unit={}: {}", source.sessionId(), unitIndex, e.toString());
            targets.forEach(t -> reasons.put(t, "LLM 호출 실패: " + e.getMessage()));
            return;
        }
        if (generated == null || generated.questions() == null) {
            return;
        }
        for (GeneratedQuestions.Item item : generated.questions()) {
            QuestionTarget target = item == null ? null : byId.get(item.targetId());
            if (target == null || contents.containsKey(target)) {
                continue;
            }
            String problem = problem(target.type(), item);
            if (problem == null) {
                contents.put(target, content(target.type(), item));
                reasons.remove(target);
            } else {
                reasons.put(target, problem);
            }
        }
    }

    /** 형식 오류가 있으면 이유를, 없으면 null을 돌려준다. */
    private static @Nullable String problem(QuestionType type, GeneratedQuestions.Item item) {
        if (isBlank(item.question())) {
            return "문제 문장이 비어 있습니다.";
        }
        List<String> criteria = item.answerCriteria();
        if (criteria == null || criteria.isEmpty() || criteria.stream().anyMatch(QuestionDrafter::isBlank)) {
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
            if (choices == null || choices.size() != CHOICE_COUNT || choices.stream().anyMatch(QuestionDrafter::isBlank)) {
                return "객관식 보기가 " + CHOICE_COUNT + "개가 아닙니다.";
            }
            if (new HashSet<>(choices.stream().map(String::strip).toList()).size() != CHOICE_COUNT) {
                return "객관식 보기가 중복됩니다.";
            }
            Integer correct = item.correctChoiceIndex();
            if (correct == null || correct < 0 || correct >= CHOICE_COUNT) {
                return "객관식 정답 위치가 없습니다.";
            }
        }
        return null;
    }

    private static Question.Content content(QuestionType type, GeneratedQuestions.Item item) {
        boolean multipleChoice = type == QuestionType.MULTIPLE_CHOICE;
        return new Question.Content(
                item.question().strip(),
                multipleChoice ? item.choices().stream().map(String::strip).toList() : List.of(),
                multipleChoice ? item.correctChoiceIndex() : null,
                item.answerCriteria().stream().map(String::strip).toList(),
                item.modelAnswer().strip(),
                item.hint().strip(),
                item.explanation().strip());
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
