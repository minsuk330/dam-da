package com.khack.review.collection.domain;

import com.khack.review.common.json.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SessionStore {

    private final Path file;
    private final Clock clock;

    public SessionStore(Path file, Clock clock) {
        this.file = file.toAbsolutePath();
        this.clock = clock;
    }

    public synchronized SavedSession save(SessionInput input, List<String> warnings) {
        SavedSession session = new SavedSession(
                UUID.randomUUID().toString(),
                Instant.now(clock).toString(),
                "connector",
                "model_transcribed",
                input.userTurns(),
                input.reviewUnits(),
                input.topicHint(),
                List.copyOf(warnings),
                null,
                null);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.MAPPER.writeValueAsString(session) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return session;
    }

    public synchronized List<SavedSession> list() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> Json.MAPPER.readValue(line, SavedSession.class))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<SavedSession> latest() {
        List<SavedSession> all = list();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
    }
}
