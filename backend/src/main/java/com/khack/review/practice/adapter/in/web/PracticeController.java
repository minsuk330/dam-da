package com.khack.review.practice.adapter.in.web;

import com.khack.review.analysis.application.LearningSessionNotFoundException;
import com.khack.review.practice.application.PracticeNotFoundException;
import com.khack.review.practice.application.PracticeService;
import com.khack.review.practice.domain.AidType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 풀이 진행: 첫 학습 시작, 문제 제시, 도움 노출 기록, 답변 제출 (스펙 §6.4.5). */
@RestController
class PracticeController {

    private final PracticeService practice;

    PracticeController(PracticeService practice) {
        this.practice = practice;
    }

    record AidRequest(AidType type) {
    }

    record ErrorResponse(String code, String message) {
    }

    @PostMapping("/api/sessions/{sessionId}/first-study/practice")
    PracticeService.PracticeView startFirstStudy(@PathVariable Long sessionId) {
        return practice.startFirstStudy(sessionId);
    }

    @GetMapping("/api/practice/{practiceId}/next")
    PracticeService.Next next(@PathVariable Long practiceId) {
        return practice.next(practiceId);
    }

    @PostMapping("/api/practice/presentations/{presentationId}/aids")
    PracticeService.AidView recordAid(@PathVariable Long presentationId, @RequestBody AidRequest request) {
        return practice.recordAid(presentationId, request.type());
    }

    @PostMapping("/api/practice/presentations/{presentationId}/attempts")
    PracticeService.AttemptView submit(@PathVariable Long presentationId, @RequestBody PracticeService.Submission submission) {
        return practice.submit(presentationId, submission);
    }

    @ExceptionHandler({PracticeNotFoundException.class, LearningSessionNotFoundException.class})
    ResponseEntity<ErrorResponse> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("not_found", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("invalid_state", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_submission", e.getMessage()));
    }
}
