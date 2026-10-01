package com.khack.review.common.adapter.in.web.dev;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.domain.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 전 단계의 현재 사용자 전환. 합성 기록 사용자로 개인화 화면을 시연하고 기본 데모 사용자로 돌아올 때 쓴다.
 * 접근 제한은 {@link DevToolsFilter}가 한다.
 */
@RestController
class DevCurrentUserController {

    private final CurrentUser currentUser;

    DevCurrentUserController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    record SwitchRequest(String name) {
    }

    record UserView(Long id, String name, boolean demoUser) {
    }

    record UserError(String message) {
    }

    @GetMapping("/dev/current-user")
    UserView current() {
        return view(currentUser.current());
    }

    @PostMapping("/dev/current-user")
    ResponseEntity<?> switchTo(@RequestBody SwitchRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(new UserError("name이 필요합니다."));
        }
        return ResponseEntity.ok(view(currentUser.switchTo(request.name().strip())));
    }

    @PostMapping("/dev/current-user/reset")
    UserView reset() {
        return view(currentUser.reset());
    }

    private UserView view(AppUser user) {
        return new UserView(user.getId(), user.getName(), currentUser.isDemoUser());
    }
}
