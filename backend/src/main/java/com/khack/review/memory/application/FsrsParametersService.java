package com.khack.review.memory.application;

import com.khack.review.memory.domain.FsrsParameters;
import com.khack.review.memory.domain.FsrsParametersRepository;
import com.khack.review.memory.domain.FsrsSchedulers;
import com.khack.review.memory.domain.ParameterSource;
import com.khack.review.memory.domain.UserFsrsSettings;
import com.khack.review.memory.domain.UserFsrsSettingsRepository;
import io.github.openspacedrepetition.Scheduler;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
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

    /** 매개변수 묶음 하나. {@code validation}은 옵티마이저 검증 결과 JSON이며 기본값은 null이다. */
    public record ParameterSet(int version, ParameterSource source, double[] weights, @Nullable String validation) {
    }

    @Transactional
    public Active activeFor(Long userId, double desiredRetention) {
        FsrsParameters active = active(userId);
        Scheduler scheduler = schedulers.computeIfAbsent(active.getVersion() + "@" + desiredRetention,
                key -> FsrsSchedulers.create(active.weights(), desiredRetention));
        return new Active(active.getVersion(), scheduler);
    }

    /** 사용자가 지금 쓰는 매개변수 묶음. */
    @Transactional
    public ParameterSet activeParameters(Long userId) {
        return toSet(active(userId));
    }

    /**
     * 옵티마이저가 검증을 통과시킨 매개변수를 새 버전으로 저장한다(스펙 §6.4.9). 적용(활성화)은 하지 않는다.
     * 개선 여부 판정은 옵티마이저 몫이고, 여기서는 매개변수 개수만 확인한다.
     */
    @Transactional
    public ParameterSet registerOptimized(double[] weights, String validation) {
        int version = parameters.findTopByOrderByVersionDesc().map(FsrsParameters::getVersion).orElse(DEFAULT_VERSION) + 1;
        return toSet(parameters.save(new FsrsParameters(version, weights, ParameterSource.OPTIMIZED, validation, clock.instant())));
    }

    /** 저장된 매개변수 버전 목록. 롤백 대상을 고를 때 쓴다. */
    @Transactional(readOnly = true)
    public List<ParameterSet> all() {
        return parameters.findAllByOrderByVersionAsc().stream().map(FsrsParametersService::toSet).toList();
    }

    /**
     * 사용자가 쓸 매개변수 버전을 바꾼다. 기본값(버전 1)으로 되돌리거나 다른 버전으로 롤백할 수 있다.
     * 저장된 OPTIMIZED 버전은 옵티마이저 검증을 통과한 것뿐이다(스펙 §6.4.9). 기억 상태 재계산은 호출하는 쪽이 한다.
     */
    @Transactional
    public ParameterSet activate(Long userId, int version) {
        FsrsParameters target = version == DEFAULT_VERSION ? defaults() : parameters.findByVersion(version)
                .orElseThrow(() -> new IllegalArgumentException("매개변수 버전이 없습니다: " + version));
        settings.findById(userId).ifPresentOrElse(
                current -> current.activate(version),
                () -> settings.save(new UserFsrsSettings(userId, version)));
        return toSet(target);
    }

    private FsrsParameters active(Long userId) {
        int version = settings.findById(userId).map(UserFsrsSettings::getActiveParametersVersion).orElse(DEFAULT_VERSION);
        return version == DEFAULT_VERSION ? defaults() : parameters.findByVersion(version).orElseThrow();
    }

    private static ParameterSet toSet(FsrsParameters set) {
        return new ParameterSet(set.getVersion(), set.getSource(), set.weights(), set.getValidation());
    }

    private FsrsParameters defaults() {
        return parameters.findByVersion(DEFAULT_VERSION).orElseGet(() -> parameters.save(new FsrsParameters(
                DEFAULT_VERSION, FsrsSchedulers.defaultParameters(), ParameterSource.DEFAULT, null, clock.instant())));
    }
}
