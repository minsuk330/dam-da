package com.khack.review.common.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.common.application.AuthTokenService;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.application.SocialSignInService;
import com.khack.review.common.application.SocialSignInService.SocialProfile;
import com.khack.review.common.application.UnauthenticatedException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** 스펙 §7.9: 앱 API는 앱 토큰이 있어야 하고, 사용자마다 자기 데이터만 본다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.auth.required=true")
class AuthIT {

    @Value("${local.server.port}")
    int port;

    @Value("${review.auth.server-url}")
    String issuer;

    @Autowired
    AuthTokenService tokens;

    @Autowired
    SocialSignInService signIn;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    JwtEncoder encoder;

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String accessToken(String body) {
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void apiNeedsAnAppTokenAndAuthEndpointsStayOpen() throws Exception {
        assertThat(send("GET", "/api/learning-sessions", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/me", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/learning-sessions", null, null).headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
                header -> assertThat(header).startsWith("Bearer"));

        HttpResponse<String> options = send("GET", "/api/auth/options", null, null);
        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(options.body()).contains("\"providers\":[]");
    }

    @Test
    void demoLoginIsGone() throws Exception {
        assertThat(send("POST", "/api/auth/demo", null, null).statusCode()).isIn(401, 404);
    }

    @Test
    void loginCodeIsExchangedOnceForATokenOfThatUser() throws Exception {
        Long user = signIn.signIn(new SocialProfile("google", "auth-it-1", "민수", null)).getId();
        String code = tokens.issueCode(user);

        HttpResponse<String> exchanged = send("POST", "/api/auth/token", null, "{\"code\":\"" + code + "\"}");
        assertThat(exchanged.statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/me", accessToken(exchanged.body()), null).body()).contains("\"id\":" + user, "\"name\":\"민수\"");

        assertThat(send("POST", "/api/auth/token", null, "{\"code\":\"" + code + "\"}").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/token", null, "{\"code\":\"made-up\"}").statusCode()).isEqualTo(401);
    }

    @Test
    void usersSeeOnlyTheirOwnSessions() throws Exception {
        Long user = signIn.signIn(new SocialProfile("kakao", "auth-it-2", "새 사용자", null)).getId();
        String token = tokens.issue(user).accessToken();

        HttpResponse<String> sessions = send("GET", "/api/learning-sessions", token, null);
        assertThat(sessions.statusCode()).isEqualTo(200);
        assertThat(sessions.body()).isEqualTo("[]");
    }

    @Test
    void rejectsTokensThatAreNotAppTokens() throws Exception {
        Instant now = Instant.now();
        String connectorLike = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder().issuer(issuer).subject(currentUser.demoUserId().toString()).audience(List.of("https://example.com/mcp"))
                        .issuedAt(now).expiresAt(now.plusSeconds(300)).build())).getTokenValue();

        assertThat(send("GET", "/api/me", connectorLike, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/me", "not-a-jwt", null).statusCode()).isEqualTo(401);
    }

    @Test
    void outsideARequestThereIsNoCurrentUser() {
        assertThatThrownBy(currentUser::id).isInstanceOf(UnauthenticatedException.class);
    }
}
