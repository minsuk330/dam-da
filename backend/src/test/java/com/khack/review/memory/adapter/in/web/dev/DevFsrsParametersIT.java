package com.khack.review.memory.adapter.in.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.FsrsParametersService;
import com.khack.review.memory.domain.FsrsParameters;
import com.khack.review.memory.domain.FsrsParametersRepository;
import com.khack.review.memory.domain.FsrsSchedulers;
import com.khack.review.memory.domain.ParameterSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevFsrsParametersIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    int port;

    @Autowired
    FsrsParametersRepository repository;

    @Autowired
    FsrsParametersService service;

    @Autowired
    CurrentUser currentUser;

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void activeReturnsTheDefaultParametersBeforeAnyOptimization() throws Exception {
        HttpResponse<String> response = send("GET", "/dev/fsrs-parameters/active", null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("version").asInt()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
        assertThat(body.get("source").asString()).isEqualTo("DEFAULT");
        assertThat(body.get("weights")).hasSize(FsrsSchedulers.PARAMETER_COUNT);
    }

    @Test
    void registerStoresANewOptimizedVersionWithoutActivatingIt() throws Exception {
        double[] weights = FsrsSchedulers.defaultParameters();
        weights[0] = 0.4;
        String body = """
                {"weights": [%s], "validation": {"optimizedLogLoss": 0.31, "trainReviews": 240}}"""
                .formatted(Arrays.stream(weights).mapToObj(Double::toString).collect(Collectors.joining(",")));

        HttpResponse<String> response = send("POST", "/dev/fsrs-parameters", body);

        assertThat(response.statusCode()).isEqualTo(201);
        int version = JSON.readTree(response.body()).get("version").asInt();
        assertThat(version).isGreaterThan(FsrsParametersService.DEFAULT_VERSION);
        FsrsParameters saved = repository.findByVersion(version).orElseThrow();
        assertThat(saved.getSource()).isEqualTo(ParameterSource.OPTIMIZED);
        assertThat(saved.weights()).containsExactly(weights);
        assertThat(JSON.readTree(saved.getValidation()).get("trainReviews").asInt()).isEqualTo(240);
        assertThat(service.activeParameters(currentUser.id()).version()).isEqualTo(FsrsParametersService.DEFAULT_VERSION);
    }

    @Test
    void rejectsWrongParameterCountOrMissingValidation() throws Exception {
        assertThat(send("POST", "/dev/fsrs-parameters", """
                {"weights": [0.1, 0.2], "validation": {}}""").statusCode()).isEqualTo(400);
        assertThat(send("POST", "/dev/fsrs-parameters", """
                {"weights": [0.1, 0.2]}""").statusCode()).isEqualTo(400);
    }
}
