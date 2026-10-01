package com.khack.review.memory.application;

import com.khack.review.memory.domain.MemoryState;
import com.khack.review.memory.domain.MemoryStateRepository;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.State;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기억 항목의 FSRS 상태를 갱신하고 조회한다 (스펙 §6.4). 모든 시각은 주입된 {@link Clock} 기준이다.
 * 등급을 어떻게 정하는지(§6.4.5)와 언제 복습할지 고르는 일(§6.4.4)은 이 서비스가 하지 않는다.
 */
@Service
public class MemoryStateService {

    private final MemoryStateRepository states;
    private final FsrsParametersService parameters;
    private final Clock clock;
    private final double defaultRetention;

    public MemoryStateService(MemoryStateRepository states, FsrsParametersService parameters, Clock clock,
            @Value("${review.memory.default-retention:0.9}") double defaultRetention) {
        this.states = states;
        this.parameters = parameters;
        this.clock = clock;
        this.defaultRetention = defaultRetention;
    }

    /**
     * 등급 반영 결과. {@code retrievabilityBefore}는 반영 직전의 예측 R로, 처음 등급을 받은 항목이면 비어 있다.
     */
    public record ReviewResult(Long memoryItemId, Rating rating, Instant reviewedAt, Optional<Double> retrievabilityBefore,
            State state, double stability, double difficulty, Instant due, int parametersVersion) {
    }

    /** 지금 시각으로 등급 하나를 반영한다. 상태가 없으면 만든다. */
    @Transactional
    public ReviewResult review(Long userId, Long memoryItemId, Rating rating) {
        return review(userId, memoryItemId, rating, clock.instant());
    }

    /** 지정한 시각으로 등급 하나를 반영한다(대화 시각의 초기 평가 등). */
    @Transactional
    public ReviewResult review(Long userId, Long memoryItemId, Rating rating, Instant reviewedAt) {
        MemoryState state = states.findByMemoryItemId(memoryItemId)
                .orElseGet(() -> new MemoryState(userId, memoryItemId, defaultRetention, reviewedAt));
        FsrsParametersService.Active active = parameters.activeFor(state.getUserId(), state.getDesiredRetention());
        Optional<Double> before = state.retrievability(active.scheduler(), reviewedAt);
        state.review(active.scheduler(), active.version(), rating, reviewedAt);
        states.save(state);
        return new ReviewResult(memoryItemId, rating, reviewedAt, before, state.getState(), state.getStability(),
                state.getDifficulty(), state.getDue(), active.version());
    }

    /**
     * 기억 항목들의 목표 유지율을 바꾼다(기억 강도 설정, 스펙 §6.4.7). 아직 등급을 받지 않은 항목도 상태 행을 만들어
     * 유지율을 담아 두며, R과 다음 복습 시각은 여전히 첫 등급 전까지 비어 있다.
     * 이미 복습한 항목은 다음 등급부터 새 유지율로 간격을 계산한다.
     */
    @Transactional
    public void applyRetention(Long userId, List<Long> memoryItemIds, double desiredRetention) {
        for (Long itemId : memoryItemIds) {
            MemoryState state = states.findByMemoryItemId(itemId)
                    .orElseGet(() -> new MemoryState(userId, itemId, desiredRetention, clock.instant()));
            state.changeDesiredRetention(desiredRetention);
            states.save(state);
        }
    }

    /** 등급을 한 번이라도 받았는가. 기억 강도만 정해 둔 상태 행은 아직 받지 않은 것이다. */
    @Transactional(readOnly = true)
    public boolean isReviewed(Long memoryItemId) {
        return states.findByMemoryItemId(memoryItemId).filter(state -> state.getLastReview() != null).isPresent();
    }

    /** 지금 떠올릴 수 있는 확률 R. 아직 등급을 받은 적 없는 항목이면 비어 있다("아직 확인 전"). */
    @Transactional(readOnly = true)
    public Optional<Double> retrievability(Long memoryItemId) {
        return states.findByMemoryItemId(memoryItemId).flatMap(state ->
                state.retrievability(parameters.activeFor(state.getUserId(), state.getDesiredRetention()).scheduler(), clock.instant()));
    }

    /** 다음 복습 시각. 아직 등급을 받은 적 없는 항목이면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<Instant> nextReviewAt(Long memoryItemId) {
        return states.findByMemoryItemId(memoryItemId).filter(state -> state.getLastReview() != null).map(MemoryState::getDue);
    }
}
