package com.khack.review.memory.application;

import com.khack.review.memory.application.FsrsParametersService.ParameterSet;
import com.khack.review.memory.domain.MemoryState;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매개변수 버전을 바꾸고 사용자의 기억 상태를 새 매개변수로 다시 계산한다 (스펙 §6.4.9).
 * 이후 등급은 새 버전으로 반영되고 복습 기록에 그 버전이 남는다. 매일 학습 큐는 기억 상태의 다음 복습 시각을 읽으므로 그대로 따라온다.
 */
@Service
public class ParameterActivationService {

    private static final Logger log = LoggerFactory.getLogger(ParameterActivationService.class);

    private final FsrsParametersService parameters;
    private final MemoryStateRepository states;
    private final ReviewLogRepository logs;

    public ParameterActivationService(FsrsParametersService parameters, MemoryStateRepository states, ReviewLogRepository logs) {
        this.parameters = parameters;
        this.states = states;
        this.logs = logs;
    }

    /**
     * 전환 결과. {@code skipped}는 재계산할 근거가 모자라 기존 상태를 둔 항목 수다. 초기 등급 저장 이전에 초기 평가를 받은 항목이
     * 여기에 해당한다(첫 등급 기록에 직전 경과 일수가 있는데 초기 등급이 없다).
     */
    public record Activated(ParameterSet parameters, int recomputed, int skipped) {
    }

    @Transactional
    public Activated activate(Long userId, int version) {
        ParameterSet activated = parameters.activate(userId, version);
        Map<Long, List<ReviewLog>> rated = logs.findByUserIdAndRatingIsNotNullOrderByReviewedAtAscIdAsc(userId).stream()
                .collect(Collectors.groupingBy(ReviewLog::getMemoryItemId));
        int recomputed = 0;
        int skipped = 0;
        for (MemoryState state : states.findByUserId(userId)) {
            if (state.getLastReview() == null) {
                continue;
            }
            List<ReviewLog> history = rated.getOrDefault(state.getMemoryItemId(), List.of());
            if (!replayable(state, history)) {
                log.warn("기억 항목 {}: 초기 등급 없이 시작 전 상태가 있어 재계산하지 않음", state.getMemoryItemId());
                skipped++;
                continue;
            }
            FsrsParametersService.Active active = parameters.activeFor(userId, state.getDesiredRetention());
            state.replay(active.scheduler(), active.version(), history.stream()
                    .map(review -> new MemoryState.Replay(review.getRating(), review.getReviewedAt()))
                    .toList());
            recomputed++;
        }
        log.info("사용자 {} 매개변수 버전 {} 적용: 재계산 {}, 건너뜀 {}", userId, version, recomputed, skipped);
        return new Activated(activated, recomputed, skipped);
    }

    /** 첫 등급부터 모두 다시 적용할 수 있는가. 첫 등급 기록은 직전 경과 일수가 없다. */
    private static boolean replayable(MemoryState state, List<ReviewLog> history) {
        return state.isSeeded() || (!history.isEmpty() && history.getFirst().getElapsedDays() == null);
    }
}
