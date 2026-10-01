package com.khack.review.common.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 실제 시각에 오프셋을 더해 돌려주는 시계. 시간 이동 데모 모드에서 "며칠 뒤"를 바로 보여주기 위해
 * 오프셋을 앞으로만 옮길 수 있다. 오프셋이 0이면 실제 시각과 같다.
 */
public class TimeTravelClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset;

    public TimeTravelClock(Clock base) {
        this(base, new AtomicReference<>(Duration.ZERO));
    }

    private TimeTravelClock(Clock base, AtomicReference<Duration> offset) {
        this.base = base;
        this.offset = offset;
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    /** 오프셋은 공유한다. 영역만 바꾼 시계도 같이 이동한다. */
    @Override
    public Clock withZone(ZoneId zone) {
        return new TimeTravelClock(base.withZone(zone), offset);
    }

    public Duration offset() {
        return offset.get();
    }

    public void travel(Duration amount) {
        if (amount.isNegative()) {
            throw new IllegalArgumentException("시간은 앞으로만 이동할 수 있습니다: " + amount);
        }
        offset.updateAndGet(current -> current.plus(amount));
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }
}
