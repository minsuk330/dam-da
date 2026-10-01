package com.khack.review.common.adapter.in.web.connector;

import static com.khack.review.common.adapter.in.web.connector.ConnectorPages.escape;
import static com.khack.review.common.adapter.in.web.connector.ConnectorPages.page;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.UnauthenticatedException;
import com.khack.review.common.config.SocialLoginConfig.SocialRegistrations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 커넥터 연결 중 Claude가 연 브라우저 창의 화면(스펙 §7.9).
 * <ul>
 *   <li>{@code /login}: 구글·카카오 로그인. 앱과 같은 소셜 로그인이라 같은 계정으로 이어진다.</li>
 *   <li>{@code /connect/consent}: "Claude가 학습 대화 저장을 요청합니다" 허용·취소. 취소하면 Claude에 {@code access_denied}로 돌려준다.</li>
 * </ul>
 */
@Controller
class ConnectorPageController {

    private static final String CSRF = ConnectorPageController.class.getName() + ".csrf";
    /** 이 브라우저 세션에서 마지막으로 처리한 승인 요청({@code allow:}·{@code deny:} + 인가 요청). 같은 요청이 두 번 제출되면 안내만 한다. */
    private static final String DECIDED = ConnectorPageController.class.getName() + ".decided";
    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final SocialRegistrations social;
    private final RegisteredClientRepository clients;
    private final CurrentUser currentUser;
    private final SecureRandom random = new SecureRandom();

    ConnectorPageController(SocialRegistrations social, RegisteredClientRepository clients, CurrentUser currentUser) {
        this.social = social;
        this.clients = clients;
        this.currentUser = currentUser;
    }

    @GetMapping("/login")
    ResponseEntity<String> login(HttpServletRequest request, HttpServletResponse response, @RequestParam(required = false) String error) {
        boolean connecting = new HttpSessionRequestCache().getRequest(request, response) != null;
        String buttons = social.registrations().stream()
                .map(r -> "<a class=\"button %s\" href=\"/oauth2/authorization/%s\">%s로 계속하기</a>".formatted(
                        "kakao".equals(r.getRegistrationId()) ? "kakao" : "", escape(r.getRegistrationId()), escape(r.getClientName())))
                .collect(Collectors.joining());
        String body = "<h1>복습 앱 로그인</h1>"
                + "<p>" + (connecting ? "앱에서 쓰는 계정으로 로그인하면 Claude 연결을 이어서 진행합니다." : "앱에서 쓰는 계정으로 로그인하세요.") + "</p>"
                + (error != null ? "<p class=\"error\">로그인하지 못했습니다. 다시 시도해 주세요.</p>" : "")
                + (buttons.isEmpty() ? "<p class=\"error\">사용할 수 있는 로그인 방법이 없습니다.</p>" : buttons);
        return html(HttpStatus.OK, page("복습 앱 로그인", body));
    }

    @GetMapping("/connect/consent")
    ResponseEntity<String> consent(HttpServletRequest request, @RequestParam("client_id") String clientId) {
        RegisteredClient client = clients.findByClientId(clientId);
        String query = request.getQueryString();
        if (client == null || query == null) {
            return html(HttpStatus.BAD_REQUEST, page("연결할 수 없음", "<h1>연결할 수 없습니다</h1><p>등록되지 않은 연결 요청입니다. Claude에서 다시 연결해 주세요.</p>"));
        }
        String name = currentUser.current().displayName();
        String body = """
                <h1>%s 연결</h1>
                <p><b>%s</b>에서 <b>%s</b> 님의 복습 앱에 다음 권한을 요청합니다.</p>
                <ul>
                  <li>지금 나누는 학습 대화를 복습 앱에 저장</li>
                </ul>
                <p>복습 문제와 풀이 기록은 Claude로 보내지 않습니다. 복습은 앱에서 합니다.</p>
                <form method="post" action="/connect/consent" onsubmit="if (this.dataset.sent) return false; this.dataset.sent = '1';">
                  <input type="hidden" name="query" value="%s">
                  <input type="hidden" name="csrf" value="%s">
                  <button class="button primary" name="decision" value="allow">허용</button>
                  <button class="button" name="decision" value="deny">취소</button>
                </form>
                """.formatted(escape(clientName(client)), escape(clientName(client)), escape(name), escape(query), csrf(request.getSession()));
        return html(HttpStatus.OK, page("Claude 연결 승인", body));
    }

    @PostMapping("/connect/consent")
    ResponseEntity<String> decide(HttpServletRequest request, HttpServletResponse response, @RequestParam String query,
            @RequestParam String csrf, @RequestParam String decision) throws IOException {
        currentUser.id();
        HttpSession session = request.getSession();
        if (!validCsrf(session, csrf)) {
            // 이미 처리한 요청이 한 번 더 제출됐다(버튼을 두 번 누름 등). 첫 제출로 연결은 끝났으니 오류 대신 안내한다.
            Object decided = session.getAttribute(DECIDED);
            if (("allow:" + query).equals(decided)) {
                return html(HttpStatus.OK, page("연결 승인됨", "<h1>이미 연결을 승인했습니다</h1><p>이 창을 닫고 Claude로 돌아가세요.</p>"));
            }
            if (("deny:" + query).equals(decided)) {
                return html(HttpStatus.OK, page("연결 취소", "<h1>이미 연결을 취소했습니다</h1><p>이 창을 닫아도 됩니다.</p>"));
            }
            return html(HttpStatus.FORBIDDEN, page("연결할 수 없음", "<h1>요청이 만료되었습니다</h1><p>Claude에서 다시 연결해 주세요.</p>"));
        }
        session.setAttribute(DECIDED, ("allow".equals(decision) ? "allow:" : "deny:") + query);
        if ("allow".equals(decision)) {
            ConnectorConsentGate.approve(session, query);
            response.sendRedirect("/oauth2/authorize?" + query);
            return null;
        }
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUriString("/?" + query).build(true).getQueryParams();
        RegisteredClient client = clients.findByClientId(decode(params.getFirst("client_id")));
        String redirectUri = decode(params.getFirst("redirect_uri"));
        if (client == null || redirectUri == null || !client.getRedirectUris().contains(redirectUri)) {
            return html(HttpStatus.BAD_REQUEST, page("연결 취소", "<h1>연결을 취소했습니다</h1><p>이 창을 닫아도 됩니다.</p>"));
        }
        UriComponentsBuilder denied = UriComponentsBuilder.fromUriString(redirectUri).queryParam("error", "access_denied");
        String state = decode(params.getFirst("state"));
        if (state != null) {
            denied.queryParam("state", state);
        }
        response.sendRedirect(denied.encode().build().toUriString());
        return null;
    }

    private String csrf(HttpSession session) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(CSRF, token);
        return token;
    }

    private static boolean validCsrf(HttpSession session, String presented) {
        Object expected = session.getAttribute(CSRF);
        session.removeAttribute(CSRF);
        return expected instanceof String token
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private static String clientName(RegisteredClient client) {
        String name = client.getClientName();
        return name == null || name.isBlank() || name.equals(client.getClientId()) ? "Claude" : name;
    }

    private static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(HTML).body(body);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<String> unauthenticated() {
        return html(HttpStatus.UNAUTHORIZED, page("로그인 필요", "<h1>로그인이 필요합니다</h1><p>Claude에서 다시 연결해 주세요.</p>"));
    }
}
