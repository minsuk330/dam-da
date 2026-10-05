package com.khack.review.common.adapter.out.toss;

import com.khack.review.common.application.port.out.TossLoginException;
import com.khack.review.common.application.port.out.TossLoginPort;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * 앱인토스 토스 로그인 API를 mTLS로 부른다(#149). 인가 코드 → AccessToken(generate-token) → 사용자 정보(login-me)의 userKey.
 * 문서: https://developers-apps-in-toss.toss.im/documentation/common/authentication/toss-login.md
 * 인증서·키(PEM 파일 경로)가 비어 있으면 로그인을 거절한다. 테스트는 키 없이 돈다.
 */
@Component
public class TossLoginAdapter implements TossLoginPort {

    private static final String SUCCESS = "SUCCESS";

    private final RestClient restClient;
    private final boolean configured;

    @Autowired
    public TossLoginAdapter(RestClient.Builder restClientBuilder,
            @Value("${review.toss.base-url}") String baseUrl,
            @Value("${review.toss.mtls-cert:}") String certPath,
            @Value("${review.toss.mtls-key:}") String keyPath) {
        this.configured = !certPath.isBlank() && !keyPath.isBlank();
        RestClient.Builder builder = restClientBuilder.clone().baseUrl(baseUrl);
        if (configured) {
            PemSslStoreDetails keyStore = PemSslStoreDetails.forCertificate("file:" + certPath).withPrivateKey("file:" + keyPath);
            SslBundle bundle = SslBundle.of(new PemSslStoreBundle(keyStore, null));
            builder.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(HttpClientSettings.ofSslBundle(bundle)));
        }
        this.restClient = builder.build();
    }

    /** 테스트용: mTLS 없이 주어진 클라이언트로 부른다. */
    TossLoginAdapter(RestClient restClient) {
        this.restClient = restClient;
        this.configured = true;
    }

    @Override
    public TossUser login(String authorizationCode, String referrer) {
        if (!configured) {
            throw new TossLoginException("토스 mTLS 인증서가 설정되지 않았습니다 (TOSS_MTLS_CERT, TOSS_MTLS_KEY)");
        }
        JsonNode token = success(call(() -> restClient.post()
                .uri("/api-partner/v1/apps-in-toss/user/oauth2/generate-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("authorizationCode", authorizationCode, "referrer", referrer))
                .retrieve()
                .body(JsonNode.class)), "generate-token");
        String accessToken = text(token, "accessToken", "generate-token");
        JsonNode me = success(call(() -> restClient.get()
                .uri("/api-partner/v1/apps-in-toss/user/oauth2/login-me")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(JsonNode.class)), "login-me");
        return new TossUser(text(me, "userKey", "login-me"));
    }

    @Override
    public String anonymousKey(String code) {
        if (!configured) {
            throw new TossLoginException("토스 mTLS 인증서가 설정되지 않았습니다 (TOSS_MTLS_CERT, TOSS_MTLS_KEY)");
        }
        JsonNode exchanged = success(call(() -> restClient.post()
                .uri("/api-partner/v1/apps-in-toss/users/anon-key/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", code))
                .retrieve()
                .body(JsonNode.class)), "anon-key/exchange");
        return text(exchanged, "anonKey", "anon-key/exchange");
    }

    @Override
    public void disconnect(String userKey) {
        if (!configured) {
            throw new TossLoginException("토스 mTLS 인증서가 설정되지 않았습니다 (TOSS_MTLS_CERT, TOSS_MTLS_KEY)");
        }
        long key;
        try {
            key = Long.parseLong(userKey);
        } catch (NumberFormatException e) {
            throw new TossLoginException("토스 userKey가 숫자가 아님", e);
        }
        success(call(() -> restClient.post()
                .uri("/api-partner/v1/apps-in-toss/user/oauth2/access/remove-by-user-key")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userKey", key))
                .retrieve()
                .body(JsonNode.class)), "remove-by-user-key");
    }

    private interface Call {
        JsonNode run();
    }

    private static JsonNode call(Call call) {
        try {
            JsonNode body = call.run();
            if (body == null) {
                throw new TossLoginException("토스 응답이 비어 있음");
            }
            return body;
        } catch (RestClientResponseException e) {
            // 인가 코드 만료·재사용은 {"error":"invalid_grant"}로 온다.
            throw new TossLoginException("토스 호출 실패 " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new TossLoginException("토스 호출 실패: " + e.getMessage(), e);
        }
    }

    /** 성공 봉투 {@code {"resultType":"SUCCESS","success":{…}}}에서 success를 꺼낸다. 그 밖은 모두 실패다. */
    private static JsonNode success(JsonNode body, String api) {
        JsonNode result = body.get("resultType");
        JsonNode success = body.get("success");
        if (result == null || !SUCCESS.equals(result.asString()) || success == null || success.isNull()) {
            throw new TossLoginException("토스 " + api + " 실패: " + body);
        }
        return success;
    }

    private static String text(JsonNode node, String field, String api) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            throw new TossLoginException("토스 " + api + " 응답에 " + field + " 없음");
        }
        return value.asString();
    }
}
