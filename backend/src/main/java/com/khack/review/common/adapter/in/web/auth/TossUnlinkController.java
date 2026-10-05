package com.khack.review.common.adapter.in.web.auth;

import com.khack.review.common.application.TossUnlinkService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스 로그인 연결 끊기 콜백(#149). 앱이 아니라 토스 서버가 부르므로 {@code /api} 밖에 두어 API 계약에 넣지 않는다.
 * 콘솔에서 고른 방식에 따라 GET은 쿼리({@code ?userKey=…&referrer=…}), POST는 JSON 본문으로 온다. 둘 다 받는다.
 * 콘솔 등록: 콜백 URL {@code https://<서버>/toss/unlink}, Basic Auth 값은 서버 {@code TOSS_UNLINK_BASIC_AUTH}와 같게.
 */
@RestController
class TossUnlinkController {

    private final TossUnlinkService unlink;

    TossUnlinkController(TossUnlinkService unlink) {
        this.unlink = unlink;
    }

    /** 토스는 userKey를 숫자로 보낸다. */
    record UnlinkEvent(@Nullable Long userKey, @Nullable String referrer) {
    }

    @GetMapping("/toss/unlink")
    ResponseEntity<Void> unlinkByQuery(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String userKey, @RequestParam(required = false) String referrer) {
        return handle(authorization, userKey, referrer);
    }

    @PostMapping("/toss/unlink")
    ResponseEntity<Void> unlinkByBody(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) UnlinkEvent event) {
        if (event == null) {
            return handle(authorization, null, null);
        }
        return handle(authorization, event.userKey() == null ? null : event.userKey().toString(), event.referrer());
    }

    private ResponseEntity<Void> handle(String authorization, String userKey, String referrer) {
        if (!unlink.authorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        unlink.unlink(userKey, referrer);
        return ResponseEntity.ok().build();
    }
}
