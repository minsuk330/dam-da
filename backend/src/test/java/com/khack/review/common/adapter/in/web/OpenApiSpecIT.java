package com.khack.review.common.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 프론트엔드 API 계약 {@code frontend/openapi.json}이 현재 코드와 같은지 확인한다.
 * 다르면 파일을 새로 쓰고 실패한다. 다시 실행하면 통과하므로, 바뀐 파일을 커밋하고
 * {@code frontend}에서 {@code npm run api:types}로 타입을 다시 만든다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSpecIT {

    static final Path SPEC = Path.of("..", "frontend", "openapi.json");

    @Value("${local.server.port}")
    int port;

    @Test
    void committedSpecMatchesCode() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        String actual = response.body().strip() + "\n";
        String committed = Files.exists(SPEC) ? Files.readString(SPEC, StandardCharsets.UTF_8) : "";
        if (!actual.equals(committed)) {
            Files.writeString(SPEC, actual, StandardCharsets.UTF_8);
            fail("API 계약이 바뀌어 %s를 다시 썼다. 확인 후 커밋하고 frontend에서 npm run api:types를 실행한다."
                    .formatted(SPEC.toAbsolutePath().normalize()));
        }
    }
}
