package com.khack.review.collection.adapter.in.web.dev;

import com.khack.review.collection.domain.SessionStore;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only viewer for sessions received through the connector. Access is restricted by
 * {@code DevToolsFilter}.
 */
@RestController
class DevSessionsController {

    private static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final SessionStore store;

    DevSessionsController(SessionStore store) {
        this.store = store;
    }

    @GetMapping("/dev/sessions")
    ResponseEntity<String> page() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(SessionPage.render(store.list()));
    }

    @GetMapping("/dev/sessions.md")
    ResponseEntity<String> markdown() {
        return ResponseEntity.ok().contentType(MARKDOWN).body(SessionMarkdown.render(store.list()));
    }
}
