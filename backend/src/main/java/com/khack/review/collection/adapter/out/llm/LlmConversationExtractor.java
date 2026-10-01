package com.khack.review.collection.adapter.out.llm;

import com.khack.review.collection.application.port.out.ConversationExtractionException;
import com.khack.review.collection.application.port.out.ConversationExtractor;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.application.port.out.LlmPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * LLM으로 원문 대화에서 커넥터 스키마 v5를 추출한다(스펙 §6.1, §7.5). 공유 링크는 발화자가 구분되어 있으므로
 * 사용자 발화 원문은 모델에게 다시 쓰게 하지 않고 서버가 채운다. 구조 오류가 있으면 오류를 알려 한 번 더 요청한다.
 * 다른 구현(테스트 fake)이 있으면 그쪽을 쓴다. {@code ./gradlew test}에서는 실제 LLM을 부르지 않도록 빈을 만들지 않고,
 * live 테스트에서만 만든다.
 */
@Component
@Fallback
@Profile("!test | live")
public class LlmConversationExtractor implements ConversationExtractor {

    /** 모델에 보내는 대화 JSON의 글자 수 상한. */
    static final int MAX_INPUT_CHARS = 120_000;
    static final int MAX_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(LlmConversationExtractor.class);

    private final LlmPort llm;

    public LlmConversationExtractor(LlmPort llm) {
        this.llm = llm;
    }

    @Override
    public SessionInput extract(RawConversation conversation) {
        String user = ExtractionPrompt.user(conversation);
        if (user.length() > MAX_INPUT_CHARS) {
            throw new ConversationExtractionException("대화가 너무 깁니다(%d자). 학습한 부분만 입력하세요.".formatted(user.length()));
        }
        String prompt = user;
        SessionInput last = null;
        List<String> problems = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            SessionInput generated;
            try {
                generated = llm.generate(ExtractionPrompt.SYSTEM, prompt, SessionInput.class);
            } catch (RuntimeException e) {
                log.warn("대화 추출 LLM 호출 실패 ({}/{}): {}", attempt, MAX_ATTEMPTS, e.toString());
                if (attempt == MAX_ATTEMPTS) {
                    throw new ConversationExtractionException("LLM 호출에 실패했습니다: " + e.getMessage(), e);
                }
                continue;
            }
            problems = new ArrayList<>();
            last = normalize(conversation, generated, problems);
            if (last != null) {
                problems.addAll(SessionValidator.validate(last).errors());
            }
            if (problems.isEmpty()) {
                return last;
            }
            log.info("대화 추출 결과에 구조 오류 ({}/{}): {}", attempt, MAX_ATTEMPTS, problems);
            prompt = ExtractionPrompt.retry(user, problems);
        }
        if (last == null) {
            throw new ConversationExtractionException("추출 결과를 쓸 수 없습니다: " + String.join("; ", problems));
        }
        // 남은 구조 오류는 서버 검증이 같은 메시지로 거부한다.
        return last;
    }

    /**
     * 서버가 고칠 수 있는 것은 고친다: 공유 링크의 발화 원문과 순서, 빈 선택 필드. 고칠 수 없으면 {@code problems}에 적는다.
     * 발화 분류 자체를 쓸 수 없으면 null.
     */
    private static @Nullable SessionInput normalize(RawConversation raw, @Nullable SessionInput generated, List<String> problems) {
        if (generated == null || generated.userTurns() == null) {
            problems.add("userTurns: 없습니다.");
            return null;
        }
        List<UserTurn> turns = raw.pastedText() != null
                ? pastedTurns(generated.userTurns(), problems)
                : sharedTurns(raw.userTurnTexts(), generated.userTurns(), problems);
        if (!problems.isEmpty()) {
            return null;
        }
        List<ReviewUnit> units = generated.reviewUnits() == null ? List.of()
                : generated.reviewUnits().stream().filter(unit -> unit != null).map(LlmConversationExtractor::unit).toList();
        return new SessionInput(turns, units, blankToNull(generated.topicHint()));
    }

    /** 모델은 분류만 하고, 원문과 순서는 서버가 가진 사용자 발화를 따른다. */
    private static List<UserTurn> sharedTurns(List<String> originals, List<UserTurn> generated, List<String> problems) {
        Map<Integer, UserTurn> byIndex = new HashMap<>();
        generated.stream().filter(turn -> turn != null).forEach(turn -> byIndex.putIfAbsent(turn.index(), turn));
        List<UserTurn> turns = new ArrayList<>();
        for (int index = 1; index <= originals.size(); index++) {
            UserTurn turn = byIndex.get(index);
            if (turn == null || turn.intent() == null) {
                problems.add("userTurns: userTurnIndex %d번 사용자 메시지의 항목 또는 intent가 없습니다.".formatted(index));
            } else {
                turns.add(clean(turn, index, originals.get(index - 1)));
            }
        }
        return turns;
    }

    private static List<UserTurn> pastedTurns(List<UserTurn> generated, List<String> problems) {
        List<UserTurn> turns = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            UserTurn turn = generated.get(i);
            if (turn == null || turn.intent() == null) {
                problems.add("userTurns[%d]: intent가 없습니다.".formatted(i));
            } else {
                turns.add(clean(turn, turn.index(), turn.text()));
            }
        }
        return turns;
    }

    private static UserTurn clean(UserTurn turn, int index, @Nullable String text) {
        return new UserTurn(index, text, blankToNull(turn.quotedText()), turn.intent(), turn.aiVerdict(),
                blankToNull(turn.correction()));
    }

    private static ReviewUnit unit(ReviewUnit unit) {
        List<ConfusionPoint> confusions = unit.confusions();
        return new ReviewUnit(unit.title(), unit.keyPoints(), confusions.isEmpty() ? null : confusions);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
