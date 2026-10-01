package com.khack.review.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.ValidationResult;
import com.khack.review.common.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 로그인 계정 시연 세션({@code fixtures/demo-seed})은 커넥터 검증을 경고 없이 통과해야 한다. */
class DemoSeedFixturesTest {

    @Test
    void everySeedSessionPassesValidationWithoutWarnings() throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.list(Path.of("fixtures/demo-seed"))) {
            files = paths.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
        assertThat(files).hasSize(15);
        for (Path file : files) {
            ValidationResult result = SessionValidator.validate(Json.MAPPER.readValue(Files.readString(file), SessionInput.class));
            assertThat(result.errors()).as(file.toString()).isEmpty();
            assertThat(result.warnings()).as(file.toString()).isEmpty();
        }
    }
}
