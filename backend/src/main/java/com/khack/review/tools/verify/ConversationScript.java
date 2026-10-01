package com.khack.review.tools.verify;

import com.khack.review.common.json.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record ConversationScript(String name, String platform, List<String> userTurns, String shareUrl) {

    public static ConversationScript load(Path path) {
        try {
            ConversationScript script = Json.MAPPER.readValue(Files.readString(path), ConversationScript.class);
            if (script.userTurns() == null || script.userTurns().isEmpty()) {
                throw new IllegalArgumentException(path + ": userTurns must be a non-empty array");
            }
            return script;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean hasShareUrl() {
        return shareUrl != null && !shareUrl.isBlank();
    }
}
