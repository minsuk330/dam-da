package com.khack.review.practice.adapter.in.web;

import com.khack.review.analysis.application.LearningSessionNotFoundException;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.practice.application.FirstStudyQueryService;
import com.khack.review.practice.application.FirstStudySummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 첫 학습 문제 준비 상황과 승인된 문제(정답 제외), 첫 학습 완료 요약. */
@RestController
class FirstStudyController {

    private final FirstStudyQueryService firstStudy;
    private final FirstStudySummaryService summaries;
    private final CurrentUser currentUser;

    FirstStudyController(FirstStudyQueryService firstStudy, FirstStudySummaryService summaries, CurrentUser currentUser) {
        this.firstStudy = firstStudy;
        this.summaries = summaries;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/sessions/{sessionId}/first-study")
    FirstStudyQueryService.FirstStudy firstStudy(@PathVariable Long sessionId) {
        return firstStudy.firstStudy(sessionId);
    }

    @GetMapping("/api/sessions/{sessionId}/first-study/summary")
    FirstStudySummaryService.Summary summary(@PathVariable Long sessionId) {
        return summaries.summary(currentUser.id(), sessionId);
    }

    @ExceptionHandler(LearningSessionNotFoundException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
