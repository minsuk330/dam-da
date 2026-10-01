package com.khack.review.practice.adapter.out.llm;

import com.khack.review.common.application.port.out.LlmPort;
import com.khack.review.practice.application.port.out.FeedbackContent;
import com.khack.review.practice.application.port.out.FeedbackContentGenerator;
import com.khack.review.practice.application.port.out.FeedbackContentRequest;
import com.khack.review.practice.application.port.out.FeedbackGenerationException;
import com.khack.review.practice.application.port.out.PrerequisiteSuggestion;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LLM으로 힌트·개념 설명·선행 개념 제안을 만든다(스펙 §7 5단계). 학습자가 기다리는 자리이므로 다시 요청하지 않고,
 * 쓸 수 없는 결과는 {@link FeedbackGenerationException}으로 돌려 서버가 문제에 저장된 기본 힌트·설명을 쓰게 한다.
 * 여기서는 형식과 "힌트가 정답을 그대로 담았는가"만 코드로 확인한다. 근거 발화는 요청에 있던 index만 돌려준다(규칙 2, 15).
 * {@code ./gradlew test}에서는 실제 LLM을 부르지 않도록 빈을 만들지 않고, live 테스트에서만 만든다.
 */
@Component
@Fallback
@Profile("!test | live")
public class LlmFeedbackContentGenerator implements FeedbackContentGenerator {

    static final int MAX_HINT_CHARS = 300;
    static final int MAX_EXPLANATION_CHARS = 1_200;
    static final int MAX_CONCEPT_CHARS = 100;
    static final int MAX_REASON_CHARS = 400;
    /** 이보다 짧은 정답 문구는 흔한 단어일 수 있어 힌트의 정답 노출 검사에 쓰지 않는다. */
    static final int MIN_LEAK_PHRASE_CHARS = 6;

    private final LlmPort llm;

    public LlmFeedbackContentGenerator(LlmPort llm) {
        this.llm = llm;
    }

    /** LLM 응답 형태. 필드 뜻은 {@link FeedbackPrompt}의 [출력]을 따른다. */
    record Generated(@Nullable String text, @Nullable List<Integer> evidenceTurns) {
    }

    record GeneratedPrerequisite(@Nullable String concept, @Nullable String reason) {
    }

    @Override
    public FeedbackContent hint(FeedbackContentRequest request) {
        Generated generated = call(FeedbackPrompt.HINT, request, Generated.class);
        String text = text(generated == null ? null : generated.text(), MAX_HINT_CHARS, "힌트");
        if (revealsAnswer(text, request)) {
            throw new FeedbackGenerationException("힌트가 정답 문구를 그대로 담고 있습니다.");
        }
        return new FeedbackContent(text, evidence(generated.evidenceTurns(), request, false));
    }

    @Override
    public FeedbackContent explanation(FeedbackContentRequest request) {
        Generated generated = call(FeedbackPrompt.EXPLANATION, request, Generated.class);
        String text = text(generated == null ? null : generated.text(), MAX_EXPLANATION_CHARS, "개념 설명");
        return new FeedbackContent(text, evidence(generated.evidenceTurns(), request, true));
    }

    @Override
    public PrerequisiteSuggestion prerequisite(FeedbackContentRequest request) {
        GeneratedPrerequisite generated = call(FeedbackPrompt.PREREQUISITE, request, GeneratedPrerequisite.class);
        return new PrerequisiteSuggestion(
                text(generated == null ? null : generated.concept(), MAX_CONCEPT_CHARS, "선행 개념"),
                text(generated == null ? null : generated.reason(), MAX_REASON_CHARS, "선행 개념의 이유"));
    }

    private <T> T call(String system, FeedbackContentRequest request, Class<T> type) {
        try {
            return llm.generate(system, FeedbackPrompt.user(request), type);
        } catch (RuntimeException e) {
            throw new FeedbackGenerationException("LLM 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private static String text(@Nullable String value, int maxChars, String what) {
        if (value == null || value.isBlank()) {
            throw new FeedbackGenerationException(what + "이(가) 비어 있습니다.");
        }
        String text = value.strip();
        if (text.length() > maxChars) {
            throw new FeedbackGenerationException("%s이(가) 너무 깁니다(%d자, 상한 %d자).".formatted(what, text.length(), maxChars));
        }
        return text;
    }

    /**
     * 모델이 돌려준 근거 발화 중 요청에 있던 것만 남긴다. 설명은 원문 근거로 이어져야 하므로, 남은 것이 없으면 대상 항목의
     * 근거 발화 전체를 쓴다(설명은 그 항목의 내용에서 나온다).
     */
    private static List<Integer> evidence(@Nullable List<Integer> generated, FeedbackContentRequest request, boolean required) {
        Set<Integer> allowed = request.evidence().stream().map(FeedbackContentRequest.EvidenceTurn::index)
                .collect(Collectors.toSet());
        List<Integer> kept = generated == null ? List.of()
                : generated.stream().filter(index -> index != null && allowed.contains(index)).distinct().sorted().toList();
        if (kept.isEmpty() && required) {
            return allowed.stream().sorted().toList();
        }
        return kept;
    }

    /** 모범 답안, 정답 기준, 교정의 문구가 힌트에 통째로 들어 있으면 정답을 알려 준 것이다. 표현을 바꾼 노출은 여기서 잡지 못한다. */
    private static boolean revealsAnswer(String hint, FeedbackContentRequest request) {
        String normalized = normalize(hint);
        return Stream.concat(Stream.of(request.modelAnswer(), request.correction()), request.answerCriteria().stream())
                .filter(phrase -> phrase != null)
                .map(LlmFeedbackContentGenerator::normalize)
                .filter(phrase -> phrase.length() >= MIN_LEAK_PHRASE_CHARS)
                .anyMatch(normalized::contains);
    }

    private static String normalize(String text) {
        return text.replaceAll("[\\s\\p{Punct}“”‘’·]+", "").toLowerCase();
    }
}
