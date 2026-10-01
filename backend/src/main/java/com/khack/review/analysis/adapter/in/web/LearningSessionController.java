package com.khack.review.analysis.adapter.in.web;

import com.khack.review.analysis.application.LearningSessionDetail;
import com.khack.review.analysis.application.LearningSessionNotFoundException;
import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.application.LearningSessionSummary;
import com.khack.review.analysis.application.SessionConfirmationService;
import com.khack.review.collection.application.TurnContent;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습 내용 확인 (스펙 §7.1, 도메인 스토리 S1-8). 수정 요청은 재검증한 세션 상세를 돌려준다.
 * 확인 대기가 아닌 세션을 고치면 409다.
 */
@RestController
@RequestMapping("/api/learning-sessions")
class LearningSessionController {

    private final LearningSessionQueryService query;
    private final SessionConfirmationService confirmation;

    LearningSessionController(LearningSessionQueryService query, SessionConfirmationService confirmation) {
        this.query = query;
        this.confirmation = confirmation;
    }

    record ExclusionRequest(boolean excluded) {
    }

    record TurnRequest(String text, Intent intent, @Nullable AiVerdict aiVerdict, @Nullable String correction) {

        TurnContent content() {
            return new TurnContent(text, intent, aiVerdict, correction);
        }
    }

    /** {@code afterIndex} 뒤에 넣는다(0이면 맨 앞). {@code sourceOf}는 새 발화를 출처로 더할 기억 항목 ID. */
    record NewTurnRequest(int afterIndex, String text, Intent intent, @Nullable AiVerdict aiVerdict, @Nullable String correction,
            @Nullable List<Long> sourceOf) {
    }

    record ErrorResponse(String code, String message) {
    }

    @GetMapping
    List<LearningSessionSummary> list() {
        return query.list();
    }

    @GetMapping("/{sessionId}")
    LearningSessionDetail detail(@PathVariable Long sessionId) {
        return query.detail(sessionId);
    }

    @PatchMapping("/{sessionId}/units/{unitId}")
    LearningSessionDetail unit(@PathVariable Long sessionId, @PathVariable Long unitId, @RequestBody ExclusionRequest request) {
        return confirmation.setUnitExcluded(sessionId, unitId, request.excluded());
    }

    @PatchMapping("/{sessionId}/items/{itemId}")
    LearningSessionDetail item(@PathVariable Long sessionId, @PathVariable Long itemId, @RequestBody ExclusionRequest request) {
        return confirmation.setItemExcluded(sessionId, itemId, request.excluded());
    }

    @PutMapping("/{sessionId}/turns/{index}")
    LearningSessionDetail editTurn(@PathVariable Long sessionId, @PathVariable int index, @RequestBody TurnRequest request) {
        return confirmation.editTurn(sessionId, index, request.content());
    }

    @PostMapping("/{sessionId}/turns")
    ResponseEntity<LearningSessionDetail> insertTurn(@PathVariable Long sessionId, @RequestBody NewTurnRequest request) {
        TurnContent content = new TurnContent(request.text(), request.intent(), request.aiVerdict(), request.correction());
        return ResponseEntity.status(HttpStatus.CREATED).body(confirmation.insertTurn(sessionId, request.afterIndex(), content,
                request.sourceOf() == null ? List.of() : request.sourceOf()));
    }

    @PostMapping("/{sessionId}/confirm")
    LearningSessionDetail confirm(@PathVariable Long sessionId) {
        return confirmation.confirm(sessionId);
    }

    @ExceptionHandler(LearningSessionNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(LearningSessionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("not_found", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("conflict", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage()));
    }
}
