package com.khack.review.common.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.port.out.FakeTossLoginPort;
import com.khack.review.common.domain.AppUser;
import com.khack.review.common.domain.AppUserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 토스 인앱 로그인(#149): 미니앱 appLogin() 인가 코드 → 앱 토큰. 같은 토스 사용자는 같은 계정이고, userKey는 평문으로 저장하지 않는다.
 * 토스 앱에서 연결을 끊으면(콜백) 데이터가 지워지고, 앱에서 탈퇴하면 토스 연결도 끊는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.auth.required=true")
class TossLoginIT {

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        FakeTossLoginPort fakeTossLoginPort() {
            return new FakeTossLoginPort();
        }
    }

    /** application-test.yml의 review.toss.unlink-basic-auth */
    static final String CALLBACK_AUTH = "Basic " + Base64.getEncoder().encodeToString("toss:test-callback".getBytes(StandardCharsets.UTF_8));

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeTossLoginPort toss;

    @Autowired
    AppUserRepository users;

    private HttpResponse<String> send(String method, String path, String authorization, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String code) throws Exception {
        return send("POST", "/api/auth/toss", null, "{\"authorizationCode\":\"" + code + "\",\"referrer\":\"SANDBOX\"}");
    }

    private static String bearer(HttpResponse<String> login) {
        return "Bearer " + login.body().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private static String userId(String body) {
        return body.replaceAll(".*\"user\":\\{\"id\":(\\d+).*", "$1");
    }

    private AppUser stored(HttpResponse<String> login) {
        return users.findById(Long.valueOf(userId(login.body()))).orElseThrow();
    }

    @Test
    void tossCodeBecomesAnAppTokenForTheSameAccount() throws Exception {
        toss.willAccept("first", "443731104").willAccept("again", "443731104").willAccept("other", "999");

        HttpResponse<String> first = login("first");
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"name\":\"토스 사용자\"");
        assertThat(send("GET", "/api/me", bearer(first), null).statusCode()).isEqualTo(200);

        HttpResponse<String> again = login("again");
        assertThat(userId(again.body())).isEqualTo(userId(first.body()));
        assertThat(userId(login("other").body())).isNotEqualTo(userId(first.body()));
    }

    @Test
    void userKeyIsNotStoredInPlainText() throws Exception {
        toss.willAccept("plain", "556677889");

        AppUser user = stored(login("plain"));

        assertThat(user.getProvider()).isEqualTo("toss");
        assertThat(user.getProviderUserId()).doesNotContain("556677889");
        assertThat(user.getName()).doesNotContain("556677889");
        assertThat(user.getEncryptedProviderUserId()).isNotBlank().doesNotContain("556677889");
    }

    @Test
    void rejectedOrMissingCodeIs401() throws Exception {
        assertThat(login("unknown").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/toss", null, "{\"authorizationCode\":\"\",\"referrer\":\"DEFAULT\"}").statusCode())
                .isEqualTo(401);
    }

    @Test
    void unlinkCallbackDeletesTheAccount() throws Exception {
        toss.willAccept("post-user", "111").willAccept("get-user", "222");
        HttpResponse<String> byPost = login("post-user");
        HttpResponse<String> byGet = login("get-user");

        assertThat(send("POST", "/toss/unlink", CALLBACK_AUTH, "{\"userKey\":111,\"referrer\":\"UNLINK\"}").statusCode()).isEqualTo(200);
        assertThat(send("GET", "/toss/unlink?userKey=222&referrer=WITHDRAWAL_TOSS", CALLBACK_AUTH, null).statusCode()).isEqualTo(200);

        assertThat(users.findById(Long.valueOf(userId(byPost.body())))).isEmpty();
        assertThat(users.findById(Long.valueOf(userId(byGet.body())))).isEmpty();
        assertThat(send("GET", "/api/me", bearer(byPost), null).statusCode()).isEqualTo(401);
        // 토스가 이미 끊었으므로 다시 끊지 않는다.
        assertThat(toss.disconnected).doesNotContain("111", "222");
        // 같은 사용자가 다시 로그인하면 새 계정이다.
        toss.willAccept("post-user-again", "111");
        assertThat(userId(login("post-user-again").body())).isNotEqualTo(userId(byPost.body()));
    }

    @Test
    void unlinkCallbackNeedsTheConsoleCredentials() throws Exception {
        toss.willAccept("kept", "333");
        HttpResponse<String> kept = login("kept");
        String wrong = "Basic " + Base64.getEncoder().encodeToString("toss:wrong".getBytes(StandardCharsets.UTF_8));

        assertThat(send("POST", "/toss/unlink", null, "{\"userKey\":333,\"referrer\":\"UNLINK\"}").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/toss/unlink", wrong, "{\"userKey\":333,\"referrer\":\"UNLINK\"}").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/toss/unlink?userKey=333&referrer=UNLINK", bearer(kept), null).statusCode()).isEqualTo(401);
        assertThat(users.findById(Long.valueOf(userId(kept.body())))).isPresent();
        // 모르는 사용자는 조용히 200(토스가 재시도하지 않게)
        assertThat(send("POST", "/toss/unlink", CALLBACK_AUTH, "{\"userKey\":404,\"referrer\":\"UNLINK\"}").statusCode()).isEqualTo(200);
    }

    @Test
    void deletingTheAccountDisconnectsToss() throws Exception {
        toss.willAccept("leaving", "444");
        HttpResponse<String> leaving = login("leaving");

        assertThat(send("DELETE", "/api/me", bearer(leaving), null).statusCode()).isEqualTo(204);

        assertThat(toss.disconnected).contains("444");
        assertThat(users.findById(Long.valueOf(userId(leaving.body())))).isEmpty();
    }
}
