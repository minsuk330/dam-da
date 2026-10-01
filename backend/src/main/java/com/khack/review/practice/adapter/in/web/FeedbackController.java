package com.khack.review.practice.adapter.in.web;

import com.khack.review.practice.application.FeedbackService;
import com.khack.review.practice.application.PracticeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 단계적 피드백: 답한 뒤 다음 행동(힌트·설명·오늘 다시 묻기 등)을 정해 받고, 지금까지의 피드백을 읽는다 (스펙 §7 5단계). */
@RestController
class FeedbackController {

    private final FeedbackService feedback;

    FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    record ErrorResponse(String code, String message) {
    }

    @PostMapping("/api/practice/presentations/{presentationId}/feedback")
    FeedbackService.FeedbackView decide(@PathVariable Long presentationId) {
        return feedback.decide(presentationId);
    }

    @GetMapping("/api/practice/presentations/{presentationId}/feedback")
    FeedbackService.FeedbackView current(@PathVariable Long presentationId) {
        return feedback.current(presentationId);
    }

    @ExceptionHandler(PracticeNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("not_found", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("invalid_state", e.getMessage()));
    }
}
