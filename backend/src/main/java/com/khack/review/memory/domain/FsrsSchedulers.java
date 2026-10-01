package com.khack.review.memory.domain;

import io.github.openspacedrepetition.Scheduler;
import java.time.Duration;

/**
 * 이 서비스의 FSRS 스케줄러 설정 (스펙 §6.4).
 * <ul>
 *   <li>learning·relearning steps는 끈다. 같은 날 재확인은 앱 정책(§6.4.8)이 맡고, FSRS는 모든 등급에 일 단위 간격을 준다.</li>
 *   <li>간격 흔들기(fuzz)는 끈다. 시간 이동 데모와 테스트 결과가 매번 같아야 한다.</li>
 * </ul>
 */
public final class FsrsSchedulers {

    /** FSRS-6 매개변수 개수. py-fsrs 옵티마이저 결과도 이 길이여야 한다. */
    public static final int PARAMETER_COUNT = 21;

    private FsrsSchedulers() {
    }

    public static Scheduler create(double[] parameters, double desiredRetention) {
        if (parameters.length != PARAMETER_COUNT) {
            throw new IllegalArgumentException("FSRS 매개변수는 %d개여야 합니다: %d".formatted(PARAMETER_COUNT, parameters.length));
        }
        return Scheduler.builder()
                .parameters(parameters.clone())
                .desiredRetention(desiredRetention)
                .learningSteps(new Duration[0])
                .relearningSteps(new Duration[0])
                .enableFuzzing(false)
                .build();
    }

    /** java-fsrs 기본 매개변수. */
    public static double[] defaultParameters() {
        return Scheduler.builder().build().getParameters().clone();
    }
}
