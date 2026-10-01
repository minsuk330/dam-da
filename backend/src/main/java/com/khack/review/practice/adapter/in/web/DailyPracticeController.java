package com.khack.review.practice.adapter.in.web;

import com.khack.review.practice.application.DailyPracticeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 오늘의 학습: 조회(문제 수·예상 소요 시간)와 시작 (스펙 §6.4.4, §7.7). 풀이는 시작 응답의 {@code practiceId}로 {@code /api/practice/**}에서 한다. */
@RestController
class DailyPracticeController {

    private final DailyPracticeService daily;

    DailyPracticeController(DailyPracticeService daily) {
        this.daily = daily;
    }

    record ErrorResponse(String code, String message) {
    }

    @GetMapping("/api/daily")
    DailyPracticeService.DailyView today() {
        return daily.today();
    }

    @PostMapping("/api/daily/start")
    DailyPracticeService.DailyView start() {
        return daily.start();
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("invalid_state", e.getMessage()));
    }
}
