package com.khack.review.common.adapter.in.web.dev;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.domain.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 없는 개발 도구 요청(`/dev/**`)의 사용자 전환. 합성 기록 사용자로 옵티마이저를 돌리고 기본 데모 사용자로 돌아올 때 쓴다.
 * 로그인한 앱 요청에는 영향이 없다.
 * 접근 제한은 {@link DevToolsFilter}가 한다.
 */
@RestController
class DevCurrentUserController {

    private final CurrentUser currentUser;

    DevCurrentUserController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /** {@code id}가 있으면 그 기존 사용자로, 없으면 {@code name} 사용자로(없으면 만든다) 바꾼다. */
    record SwitchRequest(String name, Long id) {
    }

    record UserView(Long id, String name, boolean demoUser) {
    }

    record UserError(String message) {
    }

    @GetMapping("/dev/current-user")
    UserView current() {
        return view(currentUser.devUser());
    }

    @PostMapping("/dev/current-user")
    ResponseEntity<?> switchTo(@RequestBody SwitchRequest request) {
        if (request.id() != null) {
            try {
                return ResponseEntity.ok(view(currentUser.switchTo(request.id())));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(new UserError(e.getMessage()));
            }
        }
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(new UserError("name 또는 id가 필요합니다."));
        }
        return ResponseEntity.ok(view(currentUser.switchTo(request.name().strip())));
    }

    @PostMapping("/dev/current-user/reset")
    UserView reset() {
        return view(currentUser.reset());
    }

    private UserView view(AppUser user) {
        return new UserView(user.getId(), user.getName(), currentUser.isDemoDevUser());
    }
}
