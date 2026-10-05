package com.khack.review.memory.application;

import com.khack.review.memory.domain.MemoryState;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 합성 복습 기록을 사용자 기록으로 넣는다 (개발 도구, 개인화 시연용 — #71). 기록은 {@code optimizer/}의 합성 생성기가 만든다.
 * 항목에는 실제 대화·세션·문제가 없으므로 기억 상태와 복습 기록만 생긴다. 기본 데모 사용자에게는 쓰지 않는다(호출하는 쪽이 막는다).
 */
@Service
public class SyntheticHistoryService {

    /** 사용자 1명의 합성 항목 수 상한. 항목·풀이 ID를 사용자별로 겹치지 않게 만드는 범위다. */
    static final int MAX_CARDS = 9_999;

    private static final double MILLIS_PER_DAY = Duration.ofDays(1).toMillis();

    private final ReviewLogRepository logs;
    private final MemoryStateRepository states;
    private final FsrsParametersService parameters;
    private final double defaultRetention;

    public SyntheticHistoryService(ReviewLogRepository logs, MemoryStateRepository states, FsrsParametersService parameters,
            @Value("${review.memory.default-retention:0.9}") double defaultRetention) {
        this.logs = logs;
        this.states = states;
        this.parameters = parameters;
        this.defaultRetention = defaultRetention;
    }

    /** py-fsrs {@code ReviewLog} 형식의 합성 등급 기록 1개. {@code cardId}는 1~{@value #MAX_CARDS}. */
    public record SyntheticReview(int cardId, Rating rating, Instant reviewedAt, long responseTimeMs) {
    }

    public record Imported(int items, int reviews) {
    }

    /**
     * 사용자의 기억 상태·복습 기록을 지우고 합성 기록으로 바꾼다. 매개변수는 기본값으로 되돌리고 기본값으로 상태를 계산한다.
     * 실제 앱이 기본 매개변수로 복습 시점을 정했다고 보는 것이다. 이후 옵티마이저가 이 기록으로 학습한다.
     */
    @Transactional
    public Imported replace(Long userId, List<SyntheticReview> reviews) {
        logs.deleteByUserId(userId);
        states.deleteByUserId(userId);
        logs.flush();
        states.flush();
        parameters.activate(userId, FsrsParametersService.DEFAULT_VERSION);
        Map<Integer, List<SyntheticReview>> cards = new TreeMap<>();
        for (SyntheticReview review : reviews) {
            if (review.cardId() < 1 || review.cardId() > MAX_CARDS) {
                throw new IllegalArgumentException("card_id는 1~%d여야 합니다: %d".formatted(MAX_CARDS, review.cardId()));
            }
            cards.computeIfAbsent(review.cardId(), id -> new ArrayList<>()).add(review);
        }
        FsrsParametersService.Active active = parameters.activeFor(userId, defaultRetention);
        long attempt = 0;
        for (Map.Entry<Integer, List<SyntheticReview>> card : cards.entrySet()) {
            List<SyntheticReview> history = card.getValue().stream().sorted(Comparator.comparing(SyntheticReview::reviewedAt)).toList();
            long itemId = itemId(userId, card.getKey());
            Instant previous = null;
            for (SyntheticReview review : history) {
                logs.save(ReviewLog.synthetic(userId, itemId, -(userId * 10_000_000L + ++attempt), review.rating(),
                        review.reviewedAt(), elapsedDays(previous, review.reviewedAt()), review.responseTimeMs(), active.version()));
                previous = review.reviewedAt();
            }
            states.save(state(userId, itemId, history, active.scheduler(), active.version()));
        }
        return new Imported(cards.size(), reviews.size());
    }

    /** 실제 기억 항목 ID(양수)와 겹치지 않도록 음수를 쓴다. java-fsrs 카드 ID가 int라 사용자 ID 약 21만까지 쓸 수 있다. */
    static long itemId(Long userId, int cardId) {
        return -(userId * (MAX_CARDS + 1) + cardId);
    }

    private MemoryState state(Long userId, long itemId, List<SyntheticReview> history, Scheduler scheduler, int version) {
        MemoryState state = new MemoryState(userId, itemId, defaultRetention, history.getFirst().reviewedAt());
        state.replay(scheduler, version, history.stream()
                .map(review -> new MemoryState.Replay(review.rating(), review.reviewedAt()))
                .toList());
        return state;
    }

    private static @Nullable Double elapsedDays(@Nullable Instant previous, Instant at) {
        return previous == null ? null : Duration.between(previous, at).toMillis() / MILLIS_PER_DAY;
    }
}
