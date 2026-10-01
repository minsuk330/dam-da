package com.khack.review.memory.application;

import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import io.github.openspacedrepetition.Rating;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 항목의 복습 기록을 요약해 돌려주는 읽기 전용 조회. 첫 학습 완료 요약(스펙 §7 17단계)처럼 결과만 보여주는 곳이 쓴다. */
@Service
public class ReviewOutcomeQuery {

    private final ReviewLogRepository logs;

    public ReviewOutcomeQuery(ReviewLogRepository logs) {
        this.logs = logs;
    }

    /**
     * 항목 하나의 복습 기록 요약.
     *
     * @param latestRating 가장 최근 등급. 등급을 받은 적 없으면 null
     * @param held         등급 변환이 보류된 기록이 있는가
     * @param aidExposed   도움(힌트·해설 등)을 본 뒤의 시도가 있는가
     */
    public record Outcome(@Nullable Rating latestRating, boolean held, boolean aidExposed) {
    }

    @Transactional(readOnly = true)
    public Outcome outcome(Long memoryItemId) {
        List<ReviewLog> history = logs.findByMemoryItemIdOrderByReviewedAtAscIdAsc(memoryItemId);
        Rating latest = null;
        for (ReviewLog log : history) {
            if (log.getRating() != null) {
                latest = log.getRating();
            }
        }
        return new Outcome(latest, history.stream().anyMatch(log -> log.getHoldReason() != null),
                history.stream().anyMatch(ReviewLog::isPriorAidExposed));
    }
}
