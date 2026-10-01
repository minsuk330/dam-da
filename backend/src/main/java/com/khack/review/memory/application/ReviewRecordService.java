package com.khack.review.memory.application;

import com.khack.review.memory.domain.MemoryStateUpdated;
import com.khack.review.memory.domain.RatingDecision;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.RatingPolicy;
import com.khack.review.memory.domain.ReviewContext;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import com.khack.review.memory.domain.ReviewScheduled;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판정된 풀이 시도를 등급으로 바꾸고 복습 기록을 남긴 뒤 기억 상태를 갱신한다 (스펙 §6.4.5, 도메인 스토리 S1-15·S2-6).
 * 등급이면 FSRS로 상태와 다음 복습 시각을 갱신하고, 보류면 FSRS 상태는 두고 연속 보류만 센다.
 * 판정은 부르는 쪽이 한다(객관식은 코드 채점, 그 외는 Jev).
 */
@Service
public class ReviewRecordService {

    private static final double MILLIS_PER_DAY = Duration.ofDays(1).toMillis();

    private final RatingPolicy policy;
    private final MemoryStateService memory;
    private final ReviewLogRepository logs;
    private final ApplicationEventPublisher events;

    public ReviewRecordService(RatingPolicy policy, MemoryStateService memory, ReviewLogRepository logs,
            ApplicationEventPublisher events) {
        this.policy = policy;
        this.memory = memory;
        this.logs = logs;
        this.events = events;
    }

    /** 등급 반영 결과. 보류면 {@code review}가, 등급이면 {@code hold}가 비어 있다. 평가 대상이 아니면 둘 다 비어 있다. */
    public record Recorded(RatingDecision decision, Optional<MemoryStateService.ReviewResult> review,
            Optional<MemoryStateService.HoldResult> hold) {
    }

    /** 같은 시도를 다시 주면 기록·갱신 없이 처음 결정을 돌려준다. */
    @Transactional
    public Recorded record(ReviewContext context, RatingInput input) {
        Optional<ReviewLog> existing = logs.findByAttemptId(context.attemptId());
        if (existing.isPresent()) {
            return new Recorded(decisionOf(existing.get()), Optional.empty(), Optional.empty());
        }
        RatingDecision decision = policy.decide(input);
        switch (decision) {
            case RatingDecision.NotEvaluated ignored -> {
                return new Recorded(decision, Optional.empty(), Optional.empty());
            }
            case RatingDecision.Held held -> {
                MemoryStateService.HoldResult hold = memory.hold(context.userId(), context.memoryItemId());
                logs.save(ReviewLog.of(context, input, decision, elapsedDays(context), null, policy.minConfidenceFor(input.evidenceTranscribed())));
                return new Recorded(decision, Optional.empty(), Optional.of(hold));
            }
            case RatingDecision.Rated rated -> {
                Double elapsedDays = elapsedDays(context);
                MemoryStateService.ReviewResult result = memory.review(context.userId(), context.memoryItemId(),
                        rated.rating(), context.reviewedAt());
                logs.save(ReviewLog.of(context, input, decision, elapsedDays, result.parametersVersion(),
                        policy.minConfidenceFor(input.evidenceTranscribed())));
                events.publishEvent(new MemoryStateUpdated(context.userId(), context.memoryItemId(), rated.rating(),
                        context.reviewedAt(), result.state(), result.stability(), result.difficulty()));
                events.publishEvent(new ReviewScheduled(context.userId(), context.memoryItemId(), result.due()));
                return new Recorded(decision, Optional.of(result), Optional.empty());
            }
        }
    }

    private @Nullable Double elapsedDays(ReviewContext context) {
        return memory.lastReviewedAt(context.memoryItemId())
                .map(last -> Duration.between(last, context.reviewedAt()).toMillis() / MILLIS_PER_DAY)
                .orElse(null);
    }

    private static RatingDecision decisionOf(ReviewLog log) {
        return log.getRating() != null
                ? new RatingDecision.Rated(log.getRating(), log.getPolicyRow(), log.getPolicyVersion())
                : new RatingDecision.Held(log.getHoldReason(), log.getPolicyRow(), log.getPolicyVersion());
    }
}
