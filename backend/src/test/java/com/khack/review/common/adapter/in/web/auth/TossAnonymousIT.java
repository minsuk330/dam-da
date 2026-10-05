package com.khack.review.common.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.AgreementService;
import com.khack.review.common.application.port.out.FakeTossLoginPort;
import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 토스 인앱 기본 진입(#149, 노출 정책 A안): 로그인 없이 익명 식별키로 계정이 생기고, 대화를 처음 저장할 때만 약관 동의를 받는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.auth.required=true")
class TossAnonymousIT {

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        FakeTossLoginPort fakeTossLoginPort() {
            return new FakeTossLoginPort();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeTossLoginPort toss;

    @Autowired
    AppUserRepository users;

    private HttpResponse<String> send(String method, String path, String bearer, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> enter(String code) throws Exception {
        return send("POST", "/api/auth/toss/anonymous", null, "{\"code\":\"" + code + "\"}");
    }

    private static String bearer(HttpResponse<String> login) {
        return "Bearer " + login.body().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private static String userId(HttpResponse<String> login) {
        return login.body().replaceAll(".*\"user\":\\{\"id\":(\\d+).*", "$1");
    }

    @Test
    void anonymousCodeBecomesTheSameAccountWithoutLogin() throws Exception {
        toss.willExchangeAnonymous("first", "anon-1").willExchangeAnonymous("again", "anon-1").willExchangeAnonymous("other", "anon-2");

        HttpResponse<String> first = enter("first");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"name\":\"토스 사용자\"").contains("\"agreementRequired\":true");
        assertThat(userId(enter("again"))).isEqualTo(userId(first));
        assertThat(userId(enter("other"))).isNotEqualTo(userId(first));

        AppUser user = users.findById(Long.valueOf(userId(first))).orElseThrow();
        assertThat(user.getProvider()).isEqualTo("toss-anon");
        assertThat(user.getProviderUserId()).doesNotContain("anon-1");
    }

    @Test
    void rejectedOrMissingCodeIs401() throws Exception {
        assertThat(enter("expired").statusCode()).isEqualTo(401);
        assertThat(enter("").statusCode()).isEqualTo(401);
    }

    @Test
    void savingAConversationNeedsTheAgreementFirst() throws Exception {
        toss.willExchangeAnonymous("saver", "anon-saver");
        String bearer = bearer(enter("saver"));

        HttpResponse<String> blocked = send("POST", "/api/conversations/paste", bearer, "{\"text\":\"Q: 락이 뭐야?\\nA: 동시 접근을 막는 장치\"}");
        assertThat(blocked.statusCode()).isEqualTo(403);
        assertThat(blocked.body()).contains("\"code\":\"agreement_required\"");
        assertThat(send("POST", "/api/conversations/share-link", bearer, "{\"url\":\"https://claude.ai/share/x\"}").statusCode())
                .isEqualTo(403);

        assertThat(send("POST", "/api/me/agreements", bearer, "{\"version\":\"1999-01-01\"}").statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/me/agreements", bearer, "{\"version\":\"" + AgreementService.CURRENT_VERSION + "\"}").statusCode())
                .isEqualTo(204);

        assertThat(send("GET", "/api/me", bearer, null).body()).contains("\"agreementRequired\":false");
        assertThat(send("POST", "/api/conversations/paste", bearer, "{\"text\":\"Q: 락이 뭐야?\\nA: 동시 접근을 막는 장치\"}").statusCode())
                .isNotEqualTo(403);
    }

    @Test
    void tossLoginAccountsDoNotNeedTheInAppAgreement() throws Exception {
        toss.willAccept("login", "777");
        HttpResponse<String> login = send("POST", "/api/auth/toss", null, "{\"authorizationCode\":\"login\",\"referrer\":\"DEFAULT\"}");

        assertThat(login.body()).contains("\"agreementRequired\":false");
    }
}
