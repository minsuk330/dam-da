package com.khack.review.common.application;

import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증이 없는 해커톤 단계의 현재 사용자. 시작할 때 데모 사용자를 만들고 그 ID를 돌려준다.
 * 인증을 붙이면 이 클래스만 교체한다.
 */
@Service
public class CurrentUser implements ApplicationRunner {

    private final AppUserRepository users;
    private final Clock clock;
    private final String demoUserName;
    private volatile Long id;

    public CurrentUser(AppUserRepository users, Clock clock, @Value("${review.demo-user.name}") String demoUserName) {
        this.users = users;
        this.clock = clock;
        this.demoUserName = demoUserName;
    }

    @Override
    public void run(ApplicationArguments args) {
        id();
    }

    @Transactional
    public Long id() {
        Long cached = id;
        if (cached == null) {
            cached = users.findByName(demoUserName)
                    .orElseGet(() -> users.save(new AppUser(demoUserName, clock.instant())))
                    .getId();
            id = cached;
        }
        return cached;
    }
}
