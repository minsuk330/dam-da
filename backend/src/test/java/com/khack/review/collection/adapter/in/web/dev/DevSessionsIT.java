package com.khack.review.collection.adapter.in.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionStore;
import com.khack.review.collection.domain.UserTurn;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevSessionsIT {

    static final Path SESSIONS = createTempSessionsFile();

    static Path createTempSessionsFile() {
        try {
            return Files.createTempDirectory("dev-sessions-").resolve("sessions.jsonl");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("review.sessions-file", SESSIONS::toString);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    SessionStore store;

    @BeforeEach
    void seed() {
        if (store.list().isEmpty()) {
            UserTurn turn = new UserTurn(1, "<b>잠궈?</b> 원문", null, Intent.understanding_check, AiVerdict.corrected, "요약 & 정리");
            store.save(new SessionInput(List.of(turn),
                    List.of(new ReviewUnit("잠금 & 순서", List.of(new KeyPoint("주의 <RC>", List.of(1), FactKind.warning)), null)),
                    "InnoDB"), List.of());
        }
    }

    private HttpResponse<String> get(String path, String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void localPageShowsSessionsWithEscapedText() throws Exception {
        HttpResponse<String> response = get("/dev/sessions", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/html"));
        assertThat(response.body()).contains("&lt;b&gt;잠궈?&lt;/b&gt; 원문", "요약 &amp; 정리", "InnoDB",
                "understanding_check", "잠금 &amp; 순서", "주의 &lt;RC&gt;");
        assertThat(response.body()).doesNotContain("<b>잠궈?</b>");
    }

    @Test
    void localMarkdownDownloadReturnsRenderedMarkdown() throws Exception {
        HttpResponse<String> response = get("/dev/sessions.md", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/markdown"));
        assertThat(response.body()).startsWith("# 커넥터 수신 세션").contains("<b>잠궈?</b> 원문");
    }

    @Test
    void tunneledRequestsAreHidden() throws Exception {
        assertThat(get("/dev/sessions", "160.79.106.167").statusCode()).isEqualTo(404);
        assertThat(get("/dev/sessions.md", "160.79.106.167").statusCode()).isEqualTo(404);
    }
}
