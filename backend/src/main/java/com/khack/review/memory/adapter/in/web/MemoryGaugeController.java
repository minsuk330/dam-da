package com.khack.review.memory.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.MemoryGaugeService;
import com.khack.review.memory.application.SessionNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 학습 세션의 기억 게이지: 항목별 지금 기억할 확률과 복습 단위별 평균·가장 약한 항목. */
@RestController
class MemoryGaugeController {

    private final MemoryGaugeService gauges;
    private final CurrentUser currentUser;

    MemoryGaugeController(MemoryGaugeService gauges, CurrentUser currentUser) {
        this.gauges = gauges;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/sessions/{sessionId}/memory-gauge")
    MemoryGaugeService.SessionGauge gauge(@PathVariable Long sessionId) {
        return gauges.sessionGauge(currentUser.id(), sessionId);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
