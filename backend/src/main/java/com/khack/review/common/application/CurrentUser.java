package com.khack.review.common.application;

import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 지금 요청의 사용자(스펙 §7.9). 로그인한 요청은 인증 이름(앱 사용자 ID)을 쓴다.
 * <p>
 * 인증이 없을 때는 다음 경우에만 개발 도구 사용자로 대신한다. 시작할 때 만드는 데모 사용자가 기본이고, {@link #switchTo}로 바꾼다.
 * <ul>
 *   <li>{@code /dev/**} 요청 (옵티마이저·시간 이동 등. 접근 제한은 DevToolsFilter가 한다)</li>
 *   <li>{@code /mcp} 요청: 커넥터 OAuth(#96)를 붙이기 전까지만</li>
 *   <li>{@code review.auth.required=false} (테스트 프로파일)</li>
 * </ul>
 * 그 밖에 인증이 없으면 {@link UnauthenticatedException}. 비동기 처리처럼 요청 밖에서는 사용자 ID를 명시적으로 넘겨받는다.
 */
@Service
public class CurrentUser implements ApplicationRunner {

    private final AppUserRepository users;
    private final Clock clock;
    private final String demoUserName;
    private final boolean authRequired;
    private volatile Long devUserId;

    public CurrentUser(AppUserRepository users, Clock clock, @Value("${review.demo-user.name}") String demoUserName,
            @Value("${review.auth.required:true}") boolean authRequired) {
        this.users = users;
        this.clock = clock;
        this.demoUserName = demoUserName;
        this.authRequired = authRequired;
    }

    @Override
    public void run(ApplicationArguments args) {
        devUserId();
    }

    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            return Long.valueOf(authentication.getName());
        }
        if (!authRequired || isDevToolRequest()) {
            return devUserId();
        }
        throw new UnauthenticatedException();
    }

    @Transactional(readOnly = true)
    public AppUser current() {
        return users.findById(id()).orElseThrow(UnauthenticatedException::new);
    }

    /** 데모 사용자 ID. 개발 환경의 "데모 계정으로 시작"이 이 사용자로 로그인한다. */
    @Transactional
    public Long demoUserId() {
        return findOrCreate(demoUserName).getId();
    }

    /** 개발 도구 전용: 지금 개발 도구 사용자가 기본 데모 사용자인가. */
    public boolean isDemoDevUser() {
        return devUserId().equals(demoUserId());
    }

    /** 개발 도구 전용: 인증 없는 개발 도구 요청의 사용자를 이름으로 바꾼다(없으면 만든다). 합성 기록 사용자 시연에 쓴다. */
    @Transactional
    public AppUser switchTo(String name) {
        AppUser user = findOrCreate(name);
        devUserId = user.getId();
        return user;
    }

    /** 개발 도구 전용: 기본 데모 사용자로 돌아간다. */
    @Transactional
    public AppUser reset() {
        return switchTo(demoUserName);
    }

    @Transactional(readOnly = true)
    public AppUser devUser() {
        return users.findById(devUserId()).orElseThrow();
    }

    private Long devUserId() {
        Long cached = devUserId;
        if (cached == null) {
            cached = demoUserId();
            devUserId = cached;
        }
        return cached;
    }

    private static boolean isDevToolRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/dev/") || path.startsWith("/dev.") || path.equals("/mcp") || path.startsWith("/mcp/");
    }

    private AppUser findOrCreate(String name) {
        return users.findByName(name).orElseGet(() -> users.save(new AppUser(name, clock.instant())));
    }
}
