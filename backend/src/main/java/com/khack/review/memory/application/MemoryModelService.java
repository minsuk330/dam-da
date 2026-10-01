package com.khack.review.memory.application;

import com.khack.review.common.json.Json;
import com.khack.review.memory.application.FsrsParametersService.ParameterSet;
import com.khack.review.memory.domain.FsrsSchedulers;
import com.khack.review.memory.domain.MemoryState;
import com.khack.review.memory.domain.MemoryStateRepository;
import com.khack.review.memory.domain.ParameterSource;
import com.khack.review.memory.domain.ReviewLogRepository;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * "내 기억 패턴" 화면용 요약 (스펙 §6.4.9, #71). 사용자가 쓰는 FSRS 매개변수로 기억 주기를 계산해 보여준다.
 * 모든 값은 FSRS 계산이다(LLM·Jev를 쓰지 않는다).
 */
@Service
public class MemoryModelService {

    /** 곡선 기준 시각. 곡선은 상대 일수만 쓰므로 고정값이다. */
    private static final Instant ORIGIN = Instant.parse("2026-01-01T00:00:00Z");

    private final FsrsParametersService parameters;
    private final ReviewLogRepository logs;
    private final MemoryStateRepository states;
    private final int minReviews;
    private final int curveDays;

    public MemoryModelService(FsrsParametersService parameters, ReviewLogRepository logs, MemoryStateRepository states,
            @Value("${review.memory.personalization.min-reviews:1000}") int minReviews,
            @Value("${review.memory.personalization.curve-days:30}") int curveDays) {
        this.parameters = parameters;
        this.logs = logs;
        this.states = states;
        this.minReviews = minReviews;
        this.curveDays = curveDays;
    }

    public enum ModelStatus {
        /** 기본 매개변수. 기록이 부족하거나 아직 개선이 검증되지 않았다. */
        DEFAULT,
        /** 이 사용자 기록으로 학습하고 검증한 매개변수를 쓰는 중. */
        PERSONALIZED
    }

    /** {@code gradedReviews}는 등급이 정해진 복습 기록 수(보류 제외), {@code requiredReviews}는 개인화 학습에 필요한 최소 수. */
    public record Progress(long gradedReviews, int requiredReviews) {
    }

    /** {@code day}일 뒤 기억할 확률. */
    public record CurvePoint(int day, double retrievability) {
    }

    /**
     * @param curve                    지금 매개변수로, 오늘 처음 맞힌(Good) 지식을 기억할 확률
     * @param defaultCurve             개인화 상태일 때 같은 조건의 기본 매개변수 곡선. 기본 상태면 null
     * @param firstRecallDays          처음 맞힌 지식이 기억할 확률 90%로 떨어질 때까지 일수(초기 안정도)
     * @param typicalStabilityDays     복습한 항목들의 안정도 중앙값(일). 복습한 항목이 없으면 null
     * @param predictionImprovement    개인화 상태일 때 검증 구간 log loss 상대 개선률(0.055 = 5.5%). 기본 상태면 null
     */
    public record MemoryModel(ModelStatus status, int parametersVersion, Progress progress, List<CurvePoint> curve,
            @Nullable List<CurvePoint> defaultCurve, double firstRecallDays, @Nullable Double typicalStabilityDays,
            int reviewedItems, @Nullable Double predictionImprovement) {
    }

    @Transactional(readOnly = true)
    public MemoryModel model(Long userId) {
        ParameterSet active = parameters.activeParameters(userId);
        boolean personalized = active.source() == ParameterSource.OPTIMIZED;
        Scheduler scheduler = FsrsSchedulers.create(active.weights(), 0.9);
        List<Double> stabilities = states.findByUserId(userId).stream()
                .filter(state -> state.getLastReview() != null && state.getStability() != null)
                .map(MemoryState::getStability)
                .sorted()
                .toList();
        return new MemoryModel(
                personalized ? ModelStatus.PERSONALIZED : ModelStatus.DEFAULT,
                active.version(),
                new Progress(logs.countByUserIdAndRatingIsNotNull(userId), minReviews),
                curve(scheduler),
                personalized ? curve(FsrsSchedulers.create(FsrsSchedulers.defaultParameters(), 0.9)) : null,
                firstGood(scheduler).getStability(),
                median(stabilities),
                stabilities.size(),
                personalized ? improvement(active.validation()) : null);
    }

    private List<CurvePoint> curve(Scheduler scheduler) {
        Card card = firstGood(scheduler);
        return IntStream.rangeClosed(0, curveDays)
                .mapToObj(day -> new CurvePoint(day, scheduler.getCardRetrievability(card, ORIGIN.plus(Duration.ofDays(day)))))
                .toList();
    }

    private static Card firstGood(Scheduler scheduler) {
        return scheduler.reviewCard(Card.builder().cardId(1).due(ORIGIN).build(), Rating.GOOD, ORIGIN).card();
    }

    private static @Nullable Double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static @Nullable Double improvement(@Nullable String validation) {
        if (validation == null) {
            return null;
        }
        JsonNode node = Json.MAPPER.readTree(validation).get("improvement");
        return node == null || !node.isNumber() ? null : node.asDouble();
    }
}
