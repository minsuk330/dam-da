package com.khack.review.common.adapter.in.web.auth;

import static com.khack.review.collection.domain.Fixtures.point;
import static com.khack.review.collection.domain.Fixtures.turn;
import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.AuthTokenService;
import com.khack.review.common.application.SocialSignInService;
import com.khack.review.common.application.SocialSignInService.SocialProfile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 사용자는 남의 대화·학습 세션을 보거나 바꿀 수 없다. 남의 ID로 부르면 없는 것처럼 404다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "review.auth.required=true")
class OwnershipIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    AuthTokenService tokens;

    @Autowired
    SocialSignInService signIn;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anotherUsersConversationsAndSessionsAreNotFound() throws Exception {
        // 사용자 A가 커넥터로 보낸 대화. 사용자 B가 그 ID로 접근한다.
        Long ownerId = signIn.signIn(new SocialProfile("google", "ownership-owner", "주인", null)).getId();
        SavedSession saved;
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(ownerId.toString(), null, List.of()));
        try {
            saved = intake.intake(new SessionInput(List.of(turn(1, "표준오차가 뭐야?")),
                    List.of(new ReviewUnit("표준오차", List.of(point("표준오차는 표본평균의 퍼짐", 1)), List.of())), "통계"));
        } finally {
            SecurityContextHolder.clearContext();
        }
        var conversation = conversations.findAll().stream().filter(c -> c.getSessionId().equals(saved.id())).findFirst().orElseThrow();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        Long sessionId;
        while (true) {
            var session = sessions.findByConversationId(conversation.getId());
            if (session.isPresent() && session.get().getStatus() == LearningSessionStatus.AWAITING_CONFIRMATION) {
                sessionId = session.get().getId();
                break;
            }
            assertThat(Instant.now()).as("검수 대기").isBefore(deadline);
            Thread.onSpinWait();
        }
        assertThat(conversation.getUserId()).isEqualTo(ownerId);
        String owner = tokens.issue(ownerId).accessToken();
        String other = tokens.issue(signIn.signIn(new SocialProfile("kakao", "ownership-it", "다른 사람", null)).getId()).accessToken();

        assertThat(send("GET", "/api/conversations", owner, null).body()).contains(saved.id());
        assertThat(send("GET", "/api/conversations/" + saved.id(), owner, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/learning-sessions/" + sessionId, owner, null).statusCode()).isEqualTo(200);

        List<String> leaked = new ArrayList<>();
        if (send("GET", "/api/conversations", other, null).body().contains(saved.id())) {
            leaked.add("GET /api/conversations → 남의 대화가 목록에 있음");
        }
        if (send("GET", "/api/learning-sessions", other, null).body().contains("\"id\":" + sessionId + ",")) {
            leaked.add("GET /api/learning-sessions → 남의 세션이 목록에 있음");
        }
        Map<String, String> calls = new LinkedHashMap<>();
        calls.put("GET /api/conversations/" + saved.id(), null);
        calls.put("GET /api/learning-sessions/" + sessionId, null);
        calls.put("POST /api/learning-sessions/" + sessionId + "/confirm", null);
        calls.put("PATCH /api/learning-sessions/" + sessionId + "/items/1", "{\"excluded\":true}");
        calls.put("PATCH /api/learning-sessions/" + sessionId + "/units/1", "{\"excluded\":true}");
        calls.put("POST /api/learning-sessions/" + sessionId + "/turns", "{\"afterIndex\":1,\"text\":\"x\",\"intent\":\"understanding_check\"}");
        calls.put("PUT /api/learning-sessions/" + sessionId + "/turns/1", "{\"text\":\"x\",\"intent\":\"understanding_check\"}");
        calls.put("GET /api/sessions/" + sessionId + "/first-study", null);
        calls.put("POST /api/sessions/" + sessionId + "/first-study/practice", null);
        calls.put("GET /api/sessions/" + sessionId + "/first-study/summary", null);
        calls.put("GET /api/sessions/" + sessionId + "/learning-goals", null);
        calls.put("PUT /api/sessions/" + sessionId + "/learning-goals", "{\"goals\":[\"CORRECT_MISCONCEPTION\"]}");
        calls.put("GET /api/sessions/" + sessionId + "/memory-gauge", null);
        calls.put("GET /api/sessions/" + sessionId + "/memory-strength", null);
        calls.put("PUT /api/sessions/" + sessionId + "/memory-strength", "{\"strength\":\"UNDERSTAND\"}");
        for (Map.Entry<String, String> call : calls.entrySet()) {
            String[] parts = call.getKey().split(" ");
            int status = send(parts[0], parts[1], other, call.getValue()).statusCode();
            if (status != 404) {
                leaked.add(call.getKey() + " → " + status);
            }
        }
        assertThat(leaked).as("남의 ID로 404가 아닌 API").isEmpty();
        assertThat(sessions.findById(sessionId).orElseThrow().getStatus()).as("남이 확인하지 못함")
                .isEqualTo(LearningSessionStatus.AWAITING_CONFIRMATION);
    }
}
