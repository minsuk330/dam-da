package com.khack.review.practice.domain;

import com.khack.review.memory.domain.AttemptKind;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 답변 제출을 어떤 시도로 볼지 정한다 (스펙 §6.4.5 평가 대상 시도, §6.4.8 같은 날 재확인).
 * <ul>
 *   <li>제시의 첫 답이고 그 전에 내용 도움(힌트·설명)이 없었으면 첫 무도움 시도. 해석 도움은 내용 도움이 아니다.</li>
 *   <li>같은 날 재확인 제시라면 첫 무도움 시도 대신 지연된 무도움 재확인이고, 원래 제시에서 본 도움과 그 뒤 경과 시간을 남긴다.</li>
 *   <li>내용 도움을 본 뒤의 답은 도움 후 재시도. 두 번째 답부터는 직전 답 뒤에 내용 도움을 봐야 받는다.</li>
 * </ul>
 */
public final class AttemptRules {

    private AttemptRules() {
    }

    public record Exposure(AidType type, Instant at) {
    }

    /** 한 제시에서 지금까지 일어난 일. */
    public record History(Instant presentedAt, List<Exposure> aids, List<Instant> attempts) {
    }

    /**
     * @param interpretationHelp 이 제시에서 답 전에 해석 도움을 봤는가
     * @param priorAidExposed 직전에 내용 도움을 봤는가 (재확인이면 원래 제시에서)
     * @param sincePrior 직전 도움(없으면 원래 제시의 마지막 답) 뒤 경과 시간. 첫 무도움 시도면 null
     * @param timedFrom 응답 시간을 재기 시작한 시각: 제시 시각, 도움 후 재시도면 마지막 내용 도움 시각
     */
    public record Classification(AttemptKind kind, boolean interpretationHelp, boolean priorAidExposed,
            @Nullable Duration sincePrior, Instant timedFrom) {
    }

    public static Classification classify(History current, @Nullable History recheckOf, Instant submittedAt) {
        boolean interpretation = current.aids().stream().anyMatch(aid -> aid.type() == AidType.INTERPRETATION);
        Optional<Instant> lastContentAid = lastContentAid(current);
        Optional<Instant> lastAttempt = current.attempts().stream().max(Comparator.naturalOrder());
        if (lastAttempt.isPresent() && lastContentAid.filter(aid -> aid.isAfter(lastAttempt.get())).isEmpty()) {
            throw new IllegalStateException("다시 답하려면 먼저 힌트나 설명을 보세요. 도움 없는 답은 제시마다 한 번만 받습니다.");
        }
        if (lastContentAid.isPresent()) {
            return new Classification(AttemptKind.ASSISTED_RETRY, interpretation, true,
                    Duration.between(lastContentAid.get(), submittedAt), lastContentAid.get());
        }
        if (recheckOf != null) {
            Optional<Instant> originalAid = lastContentAid(recheckOf);
            Instant prior = originalAid.or(() -> recheckOf.attempts().stream().max(Comparator.naturalOrder()))
                    .orElse(recheckOf.presentedAt());
            return new Classification(AttemptKind.DELAYED_RECHECK, interpretation, originalAid.isPresent(),
                    Duration.between(prior, submittedAt), current.presentedAt());
        }
        return new Classification(AttemptKind.FIRST_UNASSISTED, interpretation, false, null, current.presentedAt());
    }

    /** FSRS 등급을 정하는 시도인가. 도움 후 재시도는 지식 상태 표시와 피드백에만 쓴다. */
    public static boolean isEvaluated(AttemptKind kind) {
        return kind != AttemptKind.ASSISTED_RETRY;
    }

    private static Optional<Instant> lastContentAid(History history) {
        return history.aids().stream().filter(aid -> aid.type().isContent()).map(Exposure::at).max(Comparator.naturalOrder());
    }
}
