package com.khack.review.collection.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.collection.application.port.out.ConversationExtractionException;
import com.khack.review.collection.application.port.out.ConversationExtractor;
import com.khack.review.collection.application.port.out.FakeConversationExtractor;
import com.khack.review.collection.application.port.out.ShareLinkFetcher;
import com.khack.review.collection.domain.Fidelity;
import com.khack.review.collection.domain.InputPath;
import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareLink;
import com.khack.review.collection.domain.ShareSource;
import com.khack.review.collection.domain.ShareTurn;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import com.khack.review.common.json.Json;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ConversationInputIT.Fakes.class)
class ConversationInputIT {

    static final List<ShareLink> FETCHED = new ArrayList<>();
    static ShareExtraction nextShare;
    static RuntimeException fetchFailure;

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        ShareLinkFetcher fakeShareLinkFetcher() {
            return link -> {
                FETCHED.add(link);
                if (fetchFailure != null) {
                    throw fetchFailure;
                }
                return nextShare;
            };
        }

        @Bean
        FakeConversationExtractor fakeConversationExtractor() {
            return new FakeConversationExtractor();
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    FakeConversationExtractor extractor;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    @BeforeEach
    void reset() {
        FETCHED.clear();
        fetchFailure = null;
        extractor.willReturn(null);
        nextShare = new ShareExtraction("https://chatgpt.com/share/abc", 200, "경영통계", List.of(
                new ShareTurn("user", "표준오차가 뭐야?"),
                new ShareTurn("assistant", "표본평균의 퍼짐입니다."),
                new ShareTurn("user", "그럼 표본이 크면 표준편차도 줄어?"),
                new ShareTurn("assistant", "아니요, 표준오차가 줄어듭니다.")));
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private LearningConversation stored(HttpResponse<String> response) {
        String id = Json.MAPPER.readTree(response.body()).get("conversationId").asString();
        return conversations.findAll().stream().filter(c -> c.getSessionId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void shareLinkIsFetchedExtractedStoredVerbatimAndBecomesALearningSession() throws Exception {
        HttpResponse<String> response = post("/api/conversations/share-link", "{\"url\":\"https://chatgpt.com/share/abc?x=1\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = Json.MAPPER.readTree(response.body());
        assertThat(body.get("inputPath").asString()).isEqualTo("share_link");
        assertThat(body.get("userTurnCount").asInt()).isEqualTo(2);
        assertThat(FETCHED).containsExactly(new ShareLink("https://chatgpt.com/share/abc", ShareSource.chatgpt));
        LearningConversation conversation = stored(response);
        assertThat(conversation.getInputPath()).isEqualTo(InputPath.share_link);
        assertThat(conversation.getShareSource()).isEqualTo(ShareSource.chatgpt);
        assertThat(conversation.getFidelity()).isEqualTo(Fidelity.verbatim);
        assertThat(conversation.getRawTranscript()).contains("표본평균의 퍼짐입니다.");
        assertThat(sessions.findByConversationId(conversation.getId())).hasValueSatisfying(session ->
                assertThat(body.get("learningSessionId").asLong()).as("응답의 학습 세션 ID").isEqualTo(session.getId()));
    }

    @Test
    void claudeShareLinkKeepsItsSourceInTheConversationDetail() throws Exception {
        HttpResponse<String> response = post("/api/conversations/share-link", "{\"url\":\"https://claude.ai/share/b3bc\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(FETCHED).containsExactly(new ShareLink("https://claude.ai/share/b3bc", ShareSource.claude));
        String id = Json.MAPPER.readTree(response.body()).get("conversationId").asString();
        HttpResponse<String> detail = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/conversations/" + id)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode body = Json.MAPPER.readTree(detail.body());
        assertThat(body.get("inputPath").asString()).isEqualTo("share_link");
        assertThat(body.get("shareSource").asString()).isEqualTo("claude");
    }

    @Test
    void disallowedLinkIsRejectedWithoutFetching() throws Exception {
        HttpResponse<String> response = post("/api/conversations/share-link", "{\"url\":\"http://169.254.169.254/latest/meta-data\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(FETCHED).isEmpty();
    }

    @Test
    void unavailableShareLinkSuggestsPasting() throws Exception {
        nextShare = new ShareExtraction("https://chatgpt.com/share/gone", 404, null, List.of());

        HttpResponse<String> response = post("/api/conversations/share-link", "{\"url\":\"https://chatgpt.com/share/gone\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        JsonNode body = Json.MAPPER.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo("share_unavailable");
        assertThat(body.get("fallback").asString()).isEqualTo("paste");
    }

    @Test
    void fetchErrorsAlsoSuggestPasting() throws Exception {
        fetchFailure = new RuntimeException("Timeout 45000ms exceeded");

        HttpResponse<String> response = post("/api/conversations/share-link", "{\"url\":\"https://chatgpt.com/share/slow\"}");

        assertThat(response.statusCode()).isEqualTo(422);
        JsonNode body = Json.MAPPER.readTree(response.body());
        assertThat(body.get("code").asString()).isEqualTo("share_unavailable");
        assertThat(body.get("fallback").asString()).isEqualTo("paste");
    }

    @Test
    void pastedConversationIsStoredVerbatim() throws Exception {
        HttpResponse<String> response = post("/api/conversations/paste", "{\"text\":\"표준오차가 뭐야?\\n\\n그럼 표준편차는?\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        LearningConversation conversation = stored(response);
        assertThat(conversation.getInputPath()).isEqualTo(InputPath.paste);
        assertThat(conversation.getFidelity()).isEqualTo(Fidelity.verbatim);
        assertThat(sessions.findByConversationId(conversation.getId())).hasValueSatisfying(session ->
                assertThat(Json.MAPPER.readTree(response.body()).get("learningSessionId").asLong()).isEqualTo(session.getId()));
    }

    @Test
    void extractionFailureIsReportedAndNothingIsStored() throws Exception {
        long before = conversations.count();
        extractor.willFail(new ConversationExtractionException("LLM 응답 없음"));

        HttpResponse<String> response = post("/api/conversations/paste", "{\"text\":\"표준오차가 뭐야?\"}");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(conversations.count()).isEqualTo(before);
    }

    @Test
    void blankPasteIsRejected() throws Exception {
        assertThat(post("/api/conversations/paste", "{\"text\":\"  \"}").statusCode()).isEqualTo(400);
    }
}
