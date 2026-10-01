package com.khack.review.memory.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.khack.review.memory.domain.ReviewLog;
import com.khack.review.memory.domain.ReviewLogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 매개변수 학습(스펙 §6.4.9)용 py-fsrs {@code ReviewLog} 호환 내보내기. 등급이 있는 기록만 시간순으로 내보내고
 * 보류는 뺀다. 같은 날 재확인도 별도 복습으로 들어간다.
 */
@Service
public class ReviewLogExport {

    private final ReviewLogRepository logs;

    public ReviewLogExport(ReviewLogRepository logs) {
        this.logs = logs;
    }

    /**
     * py-fsrs {@code ReviewLog} 필드. {@code card_id}는 기억 항목 ID, {@code rating}은 1(Again)~4(Easy),
     * {@code review_datetime}은 UTC ISO-8601, {@code review_duration}은 응답 시간(ms)이다.
     */
    public record PyFsrsReviewLog(
            @JsonProperty("card_id") long cardId,
            @JsonProperty("rating") int rating,
            @JsonProperty("review_datetime") String reviewDatetime,
            @JsonProperty("review_duration") long reviewDuration) {
    }

    @Transactional(readOnly = true)
    public List<PyFsrsReviewLog> pyFsrs(Long userId) {
        return logs.findByUserIdAndRatingIsNotNullOrderByReviewedAtAscIdAsc(userId).stream()
                .map(ReviewLogExport::pyFsrs)
                .toList();
    }

    private static PyFsrsReviewLog pyFsrs(ReviewLog log) {
        return new PyFsrsReviewLog(log.getMemoryItemId(), log.getRating().getValue(), log.getReviewedAt().toString(),
                log.getResponseTimeMs());
    }
}
