package com.khack.review.common.adapter.in.web.demo;

import com.khack.review.common.application.TimeTravelClock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱의 시연 도구가 쓰는 서버 시계 (스펙 §11.3 시간 이동 데모). 로그인한 사용자면 토큰 없이 쓴다(/api/** 인증).
 * {@code review.demo-clock.enabled}(DEMO_CLOCK_ENABLED)가 꺼져 있으면 404다. 시계는 사용자별이 아니라 서버 전체가 함께 움직인다.
 * 세션 뷰어 같은 나머지 개발 도구는 여전히 {@code /dev/**}에서 토큰으로 보호한다.
 */
@RestController
@RequestMapping("/api/demo/clock")
class DemoClockController {

    private final TimeTravelClock clock;
    private final boolean enabled;

    DemoClockController(TimeTravelClock clock, @Value("${review.demo-clock.enabled:false}") boolean enabled) {
        this.clock = clock;
        this.enabled = enabled;
    }

    /** {@code offset}은 ISO-8601 기간(예: "PT216H"). */
    record DemoClock(Instant now, String offset) {
    }

    record ErrorResponse(String code, String message) {
    }

    static final class DemoClockDisabledException extends RuntimeException {
        DemoClockDisabledException() {
            super("이 서버에서는 시연 도구가 꺼져 있습니다.");
        }
    }

    @GetMapping
    DemoClock current() {
        requireEnabled();
        return view();
    }

    /** 앞으로만 옮긴다. */
    @PostMapping("/travel")
    DemoClock travel(@RequestParam(defaultValue = "0") long days, @RequestParam(defaultValue = "0") long hours) {
        requireEnabled();
        Duration amount = Duration.ofDays(days).plusHours(hours);
        if (amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("days·hours로 0보다 큰 시간만큼 앞으로 이동할 수 있습니다.");
        }
        clock.travel(amount);
        return view();
    }

    @PostMapping("/reset")
    DemoClock reset() {
        requireEnabled();
        clock.reset();
        return view();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new DemoClockDisabledException();
        }
    }

    private DemoClock view() {
        return new DemoClock(clock.instant(), clock.offset().toString());
    }

    @ExceptionHandler(DemoClockDisabledException.class)
    ResponseEntity<ErrorResponse> disabled(DemoClockDisabledException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("not_found", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage()));
    }
}
