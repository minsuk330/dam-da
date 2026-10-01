package com.khack.review.collection.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConversationApiIT {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ConnectorIntakeService intake;

    @Autowired
    LearningConversationRepository conversations;

    @Autowired
    LearningSessionRepository sessions;

    private long learningSessionOf(SavedSession saved) {
        Long conversationId = conversations.findBySessionId(saved.id()).orElseThrow().getId();
        return sessions.findByConversationId(conversationId).orElseThrow().getId();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private SavedSession save() {
        List<UserTurn> turns = List.of(
                new UserTurn(1, "갭 락이 뭐야?", null, Intent.info_request, null, null),
                new UserTurn(2, "RC에서도 갭 락이 걸리지?", null, Intent.understanding_check, AiVerdict.corrected,
                        "RC에서는 갭 락을 쓰지 않는다"));
        ReviewUnit unit = new ReviewUnit("갭 락",
                List.of(new KeyPoint("갭 락은 레코드 사이 간격을 잠근다", List.of(1), null)),
                List.of(new ConfusionPoint(2, "RC에서도 갭 락이 걸린다")));
        return intake.intake(new SessionInput(turns, List.of(unit), "InnoDB 잠금"));
    }

    @Test
    void listIncludesSavedConversationWithCounts() throws Exception {
        SavedSession saved = save();

        HttpResponse<String> response = get("/api/conversations");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":\"" + saved.id() + "\",\"learningSessionId\":" + learningSessionOf(saved) + ",",
                "\"inputPath\":\"connector\"",
                "\"fidelity\":\"model_transcribed\"", "\"userTurnCount\":2", "\"reviewUnitCount\":1");
    }

    @Test
    void detailFillsDefaultsAndEvidenceTurns() throws Exception {
        SavedSession saved = save();

        HttpResponse<String> response = get("/api/conversations/" + saved.id());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"learningSessionId\":" + learningSessionOf(saved) + ",", "\"topicHint\":\"InnoDB 잠금\"", "\"aiVerdict\":\"not_applicable\"",
                "\"aiVerdict\":\"corrected\"", "\"kind\":\"fact\"", "\"evidenceTurns\":[1,2]",
                "\"userBelief\":\"RC에서도 갭 락이 걸린다\"");
    }

    @Test
    void unknownConversationIsNotFound() throws Exception {
        assertThat(get("/api/conversations/no-such-id").statusCode()).isEqualTo(404);
    }
}
