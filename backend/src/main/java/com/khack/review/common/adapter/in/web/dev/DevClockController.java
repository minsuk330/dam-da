package com.khack.review.common.adapter.in.web.dev;

import com.khack.review.common.application.TimeTravelClock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 시간 이동 데모 모드. 접근 제한은 {@link DevToolsFilter}가 한다. */
@RestController
class DevClockController {

    private final TimeTravelClock clock;

    DevClockController(TimeTravelClock clock) {
        this.clock = clock;
    }

    @GetMapping("/dev/clock")
    ClockView current() {
        return view();
    }

    @PostMapping("/dev/clock/travel")
    ResponseEntity<?> travel(@RequestParam(defaultValue = "0") long days, @RequestParam(defaultValue = "0") long hours) {
        Duration amount = Duration.ofDays(days).plusHours(hours);
        if (amount.isNegative() || amount.isZero()) {
            return ResponseEntity.badRequest().body(new ClockError("days·hours로 0보다 큰 시간만큼 앞으로 이동할 수 있습니다."));
        }
        clock.travel(amount);
        return ResponseEntity.ok(view());
    }

    @PostMapping("/dev/clock/reset")
    ClockView reset() {
        clock.reset();
        return view();
    }

    private ClockView view() {
        return new ClockView(clock.instant(), clock.offset().toString());
    }

    record ClockView(Instant now, String offset) {
    }

    record ClockError(String message) {
    }
}
