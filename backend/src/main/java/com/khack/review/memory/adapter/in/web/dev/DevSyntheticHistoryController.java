package com.khack.review.memory.adapter.in.web.dev;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.SyntheticHistoryService;
import com.khack.review.memory.application.SyntheticHistoryService.Imported;
import com.khack.review.memory.application.SyntheticHistoryService.SyntheticReview;
import io.github.openspacedrepetition.Rating;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 사용자의 기록을 합성 복습 기록으로 바꾼다(개인화 시연용, #71). 먼저 {@code POST /dev/current-user}로 합성 사용자로
 * 전환해야 하며 기본 데모 사용자에게는 거부한다. 기록 형식은 {@code GET /dev/review-logs.json}과 같다. `/dev/**` 보호를 받는다.
 */
@RestController
class DevSyntheticHistoryController {

    private final SyntheticHistoryService history;
    private final CurrentUser currentUser;

    DevSyntheticHistoryController(SyntheticHistoryService history, CurrentUser currentUser) {
        this.history = history;
        this.currentUser = currentUser;
    }

    record Review(@JsonProperty("card_id") int cardId, @JsonProperty("rating") int rating,
            @JsonProperty("review_datetime") Instant reviewDatetime, @JsonProperty("review_duration") Long reviewDuration) {
    }

    record ErrorResponse(String code, String message) {
    }

    @PostMapping("/dev/synthetic-history")
    Imported replace(@RequestBody List<Review> reviews) {
        if (currentUser.isDemoDevUser()) {
            throw new IllegalStateException("기본 데모 사용자에게는 합성 기록을 넣지 않습니다. 먼저 POST /dev/current-user로 전환하세요.");
        }
        return history.replace(currentUser.id(), toReviews(reviews));
    }

    record Link(@JsonProperty("card_id") int cardId, @JsonProperty("memory_item_id") Long memoryItemId) {
    }

    record AttachRequest(List<Link> links, List<Review> reviews) {
    }

    /** 합성 기록을 현재 사용자의 실제 기억 항목에 붙인다(로그인 계정 시연 데이터). 다른 데이터는 지우지 않는다. */
    @PostMapping("/dev/synthetic-history/attach")
    Imported attach(@RequestBody AttachRequest request) {
        if (currentUser.isDemoDevUser()) {
            throw new IllegalStateException("기본 데모 사용자에게는 합성 기록을 넣지 않습니다. 먼저 POST /dev/current-user로 전환하세요.");
        }
        return history.attach(currentUser.id(),
                request.links().stream().map(link -> new SyntheticHistoryService.Link(link.cardId(), link.memoryItemId())).toList(),
                toReviews(request.reviews()));
    }

    private static List<SyntheticReview> toReviews(List<Review> reviews) {
        return reviews.stream()
                .map(review -> new SyntheticReview(review.cardId(), Rating.values()[review.rating() - 1], review.reviewDatetime(),
                        review.reviewDuration() == null ? 0 : review.reviewDuration()))
                .toList();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, ArrayIndexOutOfBoundsException.class})
    ResponseEntity<ErrorResponse> invalid(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage()));
    }
}
