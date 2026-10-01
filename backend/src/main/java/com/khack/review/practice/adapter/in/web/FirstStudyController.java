package com.khack.review.practice.adapter.in.web;

import com.khack.review.analysis.application.LearningSessionNotFoundException;
import com.khack.review.practice.application.FirstStudyQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 첫 학습 문제 준비 상황과 승인된 문제(정답 제외). */
@RestController
class FirstStudyController {

    private final FirstStudyQueryService firstStudy;

    FirstStudyController(FirstStudyQueryService firstStudy) {
        this.firstStudy = firstStudy;
    }

    @GetMapping("/api/sessions/{sessionId}/first-study")
    FirstStudyQueryService.FirstStudy firstStudy(@PathVariable Long sessionId) {
        return firstStudy.firstStudy(sessionId);
    }

    @ExceptionHandler(LearningSessionNotFoundException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
