package com.khack.review.common.adapter.out.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.khack.review.common.application.port.out.TossLoginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 토스 로그인 API 응답 봉투 해석(#149). mTLS는 liveTest(LiveTossLoginIT)가 확인한다. */
class TossLoginAdapterTest {

    static final String TOKEN_URL = "https://toss.test/api-partner/v1/apps-in-toss/user/oauth2/generate-token";
    static final String ME_URL = "https://toss.test/api-partner/v1/apps-in-toss/user/oauth2/login-me";

    MockRestServiceServer server;
    TossLoginAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://toss.test");
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new TossLoginAdapter(builder.build());
    }

    @Test
    void exchangesTheCodeAndReadsTheUserKey() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"authorizationCode": "code-1", "referrer": "SANDBOX"}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"resultType": "SUCCESS", "success": {"tokenType": "Bearer", "accessToken": "toss-access",
                         "refreshToken": "toss-refresh", "expiresIn": 3599, "scope": "user_name"}}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ME_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer toss-access"))
                .andRespond(withSuccess("""
                        {"resultType": "SUCCESS", "success": {"userKey": 443731104, "scope": "user_name,user_key",
                         "agreedTerms": [], "name": "ENCRYPTED_VALUE", "di": null}}""", MediaType.APPLICATION_JSON));

        assertThat(adapter.login("code-1", "SANDBOX").userKey()).isEqualTo("443731104");
        server.verify();
    }

    @Test
    void expiredOrReusedCodeFails() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": "invalid_grant"}"""));

        assertThatThrownBy(() -> adapter.login("used", "DEFAULT"))
                .isInstanceOf(TossLoginException.class)
                .hasMessageContaining("invalid_grant");
    }

    @Test
    void failEnvelopeWithHttp200Fails() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("""
                        {"resultType": "FAIL", "error": {"errorCode": "4050", "reason": "인증서버에 등록된 미니앱이 아닙니다."}}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.login("code", "DEFAULT"))
                .isInstanceOf(TossLoginException.class)
                .hasMessageContaining("4050");
    }

    @Test
    void exchangesTheAnonymousAuthCode() {
        server.expect(requestTo("https://toss.test/api-partner/v1/apps-in-toss/users/anon-key/exchange"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"code": "anon-code"}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"resultType": "SUCCESS", "success": {"anonKey": "anon-hash-1"}}""", MediaType.APPLICATION_JSON));

        assertThat(adapter.anonymousKey("anon-code")).isEqualTo("anon-hash-1");
    }

    @Test
    void invalidAnonymousAuthCodeFails() {
        server.expect(requestTo("https://toss.test/api-partner/v1/apps-in-toss/users/anon-key/exchange"))
                .andRespond(withSuccess("""
                        {"resultType": "FAIL", "error": {"errorCode": "4011", "reason": "익명 사용자 인증 코드가 유효하지 않아요."}}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.anonymousKey("expired")).isInstanceOf(TossLoginException.class).hasMessageContaining("4011");
    }

    @Test
    void disconnectSendsTheNumericUserKey() {
        server.expect(requestTo("https://toss.test/api-partner/v1/apps-in-toss/user/oauth2/access/remove-by-user-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"userKey": 443731104}""", JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"resultType": "SUCCESS", "success": {"userKey": 443731104}}""", MediaType.APPLICATION_JSON));

        adapter.disconnect("443731104");
        server.verify();
    }

    @Test
    void refusesWithoutCertificates() {
        TossLoginAdapter unconfigured = new TossLoginAdapter(RestClient.builder(), "https://toss.test", "", "");

        assertThatThrownBy(() -> unconfigured.login("code", "DEFAULT"))
                .isInstanceOf(TossLoginException.class)
                .hasMessageContaining("TOSS_MTLS_CERT");
    }
}
