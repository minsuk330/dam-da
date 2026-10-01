package com.khack.review.memory.application;

import com.khack.review.memory.domain.FsrsParameters;
import com.khack.review.memory.domain.FsrsParametersRepository;
import com.khack.review.memory.domain.FsrsSchedulers;
import com.khack.review.memory.domain.ParameterSource;
import com.khack.review.memory.domain.UserFsrsSettings;
import com.khack.review.memory.domain.UserFsrsSettingsRepository;
import io.github.openspacedrepetition.Scheduler;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자별로 지금 쓰는 FSRS 매개변수와 스케줄러를 돌려준다. 시작할 때 기본 매개변수(버전 1)를 만든다.
 * 매개변수 묶음은 바뀌지 않으므로 (버전, 목표 유지율)별 스케줄러를 캐시한다.
 */
@Service
public class FsrsParametersService implements ApplicationRunner {

    public static final int DEFAULT_VERSION = 1;

    private final FsrsParametersRepository parameters;
    private final UserFsrsSettingsRepository settings;
    private final Clock clock;
    private final Map<String, Scheduler> schedulers = new ConcurrentHashMap<>();

    public FsrsParametersService(FsrsParametersRepository parameters, UserFsrsSettingsRepository settings, Clock clock) {
        this.parameters = parameters;
        this.settings = settings;
        this.clock = clock;
    }

    /** 사용자에게 지금 적용할 매개변수와, 그 매개변수·목표 유지율의 스케줄러. */
    public record Active(int version, Scheduler scheduler) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        defaults();
    }

    @Transactional
    public Active activeFor(Long userId, double desiredRetention) {
        int version = settings.findById(userId).map(UserFsrsSettings::getActiveParametersVersion).orElse(DEFAULT_VERSION);
        FsrsParameters active = version == DEFAULT_VERSION ? defaults() : parameters.findByVersion(version).orElseThrow();
        Scheduler scheduler = schedulers.computeIfAbsent(version + "@" + desiredRetention,
                key -> FsrsSchedulers.create(active.weights(), desiredRetention));
        return new Active(version, scheduler);
    }

    private FsrsParameters defaults() {
        return parameters.findByVersion(DEFAULT_VERSION).orElseGet(() -> parameters.save(new FsrsParameters(
                DEFAULT_VERSION, FsrsSchedulers.defaultParameters(), ParameterSource.DEFAULT, null, clock.instant())));
    }
}
