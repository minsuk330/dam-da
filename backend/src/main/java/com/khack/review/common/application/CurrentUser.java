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
            cached = findOrCreate(demoUserName).getId();
            id = cached;
        }
        return cached;
    }

    /** 지금 사용자가 기본 데모 사용자인가. */
    public boolean isDemoUser() {
        return id().equals(findOrCreate(demoUserName).getId());
    }

    /**
     * 개발 도구 전용: 현재 사용자를 이름으로 바꾼다(없으면 만든다). 합성 기록 사용자로 개인화 화면을 시연할 때 쓴다.
     * 인증을 붙이면 없앤다.
     */
    @Transactional
    public AppUser switchTo(String name) {
        AppUser user = findOrCreate(name);
        id = user.getId();
        return user;
    }

    /** 개발 도구 전용: 기본 데모 사용자로 돌아간다. */
    @Transactional
    public AppUser reset() {
        return switchTo(demoUserName);
    }

    @Transactional(readOnly = true)
    public AppUser current() {
        return users.findById(id()).orElseThrow();
    }

    private AppUser findOrCreate(String name) {
        return users.findByName(name).orElseGet(() -> users.save(new AppUser(name, clock.instant())));
    }
}
