package com.khack.review.common.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 키별 최근 요청 수를 세는 슬라이딩 윈도 한도(#184). 서버 한 대 기준이라 메모리에만 두고, 재시작하면 비워진다.
 * 시각은 주입받은 Clock으로 센다.
 */
@Component
public class RequestRateLimiter {

    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public RequestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** 이번 요청을 세고, {@code window} 안에 {@code limit}번을 넘었으면 거부한다. 거부된 요청은 세지 않는다. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        Instant from = now.minus(window);
        Deque<Instant> times = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
