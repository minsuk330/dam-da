package com.khack.review.common.adapter.in.web.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.khack.review.common.application.AuthTokenService;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.common.domain.ConnectorClientRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 스펙 §7.9 커넥터 연결 전체 흐름: 401 → 메타데이터 → 클라이언트 등록 → 로그인 → 연결 승인 → 코드 → 토큰 → /mcp.
 * Claude가 실제로 밟는 순서(docs/verification/2026-10-02-mcp-oauth-spike.md)를 따른다.
 */
@SpringBootTest(properties = "review.auth.required=true")
@AutoConfigureMockMvc
class ConnectorOAuthIT {

    private static final String CALLBACK = "https://claude.ai/api/mcp/auth_callback";
    private static final String VERIFIER = "connector-it-verifier-0123456789abcdefghijklmnopqrstuvwxyz";
    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},
             "clientInfo":{"name":"it","version":"1"}}}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    CurrentUser currentUser;

    @Autowired
    AuthTokenService appTokens;

    @Autowired
    ConnectorClientRepository storedClients;

    @Autowired
    RegisteredClientRepository clients;

    private final JsonMapper json = JsonMapper.builder().build();

    private OAuth2LoginRequestPostProcessor demoUser() {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("appUserId", currentUser.demoUserId().toString()), "appUserId"));
    }

    /** Claude는 비밀값을 받는 기밀 클라이언트로 등록한다(갱신 토큰은 기밀 클라이언트에만 나온다). */
    private record Registered(String clientId, String secret) {
    }

    private String registerClient() throws Exception {
        return register("none").clientId();
    }

    private Registered register(String authMethod) throws Exception {
        MvcResult registered = mvc.perform(post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"client_name":"Claude","redirect_uris":["%s"],"grant_types":["authorization_code","refresh_token"],
                         "response_types":["code"],"token_endpoint_auth_method":"%s"}""".formatted(CALLBACK, authMethod)))
                .andReturn();
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(registered.getResponse().getContentAsString());
        return new Registered(body.get("client_id").asString(), body.has("client_secret") ? body.get("client_secret").asString() : null);
    }

    private static String authorizeQuery(String clientId) throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        return "response_type=code&client_id=" + clientId + "&redirect_uri=" + URLEncoder.encode(CALLBACK, StandardCharsets.UTF_8)
                + "&code_challenge=" + challenge + "&code_challenge_method=S256&state=st-123";
    }

    private static String csrf(String html) {
        Matcher m = Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static String param(String url, String name) {
        Matcher m = Pattern.compile("[?&]" + name + "=([^&]+)").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    @Test
    void mcpAdvertisesWhereToGetAToken() throws Exception {
        MvcResult unauthorized = mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(INITIALIZE)).andReturn();
        assertThat(unauthorized.getResponse().getStatus()).isEqualTo(401);
        assertThat(unauthorized.getResponse().getHeader("WWW-Authenticate"))
                .contains("resource_metadata=http://localhost/.well-known/oauth-protected-resource/mcp");

        JsonNode resource = json.readTree(mvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andReturn().getResponse()
                .getContentAsString());
        assertThat(resource.get("authorization_servers").get(0).asString()).isEqualTo("http://localhost:8080");

        JsonNode server = json.readTree(mvc.perform(get("/.well-known/oauth-authorization-server")).andReturn().getResponse()
                .getContentAsString());
        assertThat(server.get("registration_endpoint").asString()).endsWith("/oauth2/register");
        assertThat(server.get("code_challenge_methods_supported").toString()).contains("S256");
    }

    @Test
    void connectsAfterLoginAndApprovalAndSavesAsThatUser() throws Exception {
        Registered client = register("client_secret_basic");
        String clientId = client.clientId();
        assertThat(storedClients.findByClientId(clientId)).isPresent();
        String query = authorizeQuery(clientId);
        MockHttpSession session = new MockHttpSession();

        MvcResult notLoggedIn = mvc.perform(get(URI.create("/oauth2/authorize?" + query)).accept(MediaType.TEXT_HTML).session(session)).andReturn();
        assertThat(notLoggedIn.getResponse().getRedirectedUrl()).endsWith("/login");

        MvcResult toConsent = mvc.perform(get(URI.create("/oauth2/authorize?" + query)).with(demoUser()).session(session)).andReturn();
        assertThat(toConsent.getResponse().getRedirectedUrl()).isEqualTo("/connect/consent?" + query);

        MvcResult consentPage = mvc.perform(get(URI.create("/connect/consent?" + query)).with(demoUser()).session(session)).andReturn();
        String html = consentPage.getResponse().getContentAsString();
        assertThat(html).contains("Claude", "학습 대화를 복습 앱에 저장", "허용", "지원");

        MvcResult allowed = mvc.perform(post("/connect/consent").with(demoUser()).session(session)
                .param("query", query).param("csrf", csrf(html)).param("decision", "allow")).andReturn();
        assertThat(allowed.getResponse().getRedirectedUrl()).isEqualTo("/oauth2/authorize?" + query);
        assertThat(html).as("폼은 한 번만 제출").contains("this.dataset.sent");

        // 버튼을 두 번 눌러 같은 요청이 다시 오면 만료 오류 대신 이미 승인했다고 안내한다(승인을 다시 하지는 않는다).
        MvcResult again = mvc.perform(post("/connect/consent").with(demoUser()).session(session)
                .param("query", query).param("csrf", csrf(html)).param("decision", "allow")).andReturn();
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(again.getResponse().getContentAsString()).contains("이미 연결을 승인했습니다");
        assertThat(again.getResponse().getRedirectedUrl()).isNull();

        MvcResult issued = mvc.perform(get(URI.create("/oauth2/authorize?" + query)).with(demoUser()).session(session)).andReturn();
        String callback = issued.getResponse().getRedirectedUrl();
        assertThat(callback).startsWith(CALLBACK).contains("state=st-123");

        MvcResult tokenResponse = mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code")
                .param("code", param(callback, "code")).param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER)
                .with(httpBasic(clientId, client.secret()))).andReturn();
        assertThat(tokenResponse.getResponse().getStatus()).isEqualTo(200);
        JsonNode tokens = json.readTree(tokenResponse.getResponse().getContentAsString());
        String accessToken = tokens.get("access_token").asString();
        assertThat(tokens.get("expires_in").asInt()).isGreaterThan(3_000);

        MvcResult mcp = mvc.perform(post("/mcp").header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(INITIALIZE)).andReturn();
        assertThat(mcp.getResponse().getStatus()).isEqualTo(200);

        MvcResult refreshed = mvc.perform(post("/oauth2/token").param("grant_type", "refresh_token")
                .param("refresh_token", tokens.get("refresh_token").asString()).with(httpBasic(clientId, client.secret()))).andReturn();
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(clients.findByClientId(clientId).getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(30));

        assertThat(mvc.perform(get("/api/me").header("Authorization", "Bearer " + accessToken)).andReturn().getResponse().getStatus())
                .as("커넥터 토큰으로 앱 API를 부를 수 없다").isEqualTo(401);
    }

    @Test
    void cancellingTellsClaudeAccessWasDenied() throws Exception {
        String query = authorizeQuery(registerClient());
        MockHttpSession session = new MockHttpSession();
        String html = mvc.perform(get(URI.create("/connect/consent?" + query)).with(demoUser()).session(session)).andReturn().getResponse()
                .getContentAsString();

        MvcResult denied = mvc.perform(post("/connect/consent").with(demoUser()).session(session)
                .param("query", query).param("csrf", csrf(html)).param("decision", "deny")).andReturn();

        assertThat(denied.getResponse().getRedirectedUrl()).isEqualTo(CALLBACK + "?error=access_denied&state=st-123");
    }

    @Test
    void consentNeedsTheFormToken() throws Exception {
        String query = authorizeQuery(registerClient());

        MvcResult forged = mvc.perform(post("/connect/consent").with(demoUser()).session(new MockHttpSession())
                .param("query", query).param("csrf", "guess").param("decision", "allow")).andReturn();

        assertThat(forged.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void appTokensDoNotOpenMcp() throws Exception {
        String appToken = appTokens.issue(currentUser.demoUserId()).accessToken();

        MvcResult result = mvc.perform(post("/mcp").header("Authorization", "Bearer " + appToken)
                .contentType(MediaType.APPLICATION_JSON).content(INITIALIZE)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
