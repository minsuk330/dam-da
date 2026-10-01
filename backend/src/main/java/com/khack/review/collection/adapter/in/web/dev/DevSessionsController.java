package com.khack.review.collection.adapter.in.web.dev;

import com.khack.review.collection.application.ConversationQueryService;
import com.khack.review.collection.domain.SavedSession;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    private final ConversationQueryService conversations;

    DevSessionsController(ConversationQueryService conversations) {
        this.conversations = conversations;
    }

    @GetMapping("/dev/sessions")
    ResponseEntity<String> page() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(SessionPage.render(conversations.all()));
    }

    @GetMapping("/dev/sessions.md")
    ResponseEntity<String> markdown() {
        return ResponseEntity.ok().contentType(MARKDOWN).body(SessionMarkdown.render(conversations.all()));
    }

    /** tools CLI(벤치마크, 커넥터 검증, Markdown 내보내기)가 읽는 원본. */
    @GetMapping("/dev/sessions.json")
    List<SavedSession> json() {
        return conversations.all();
    }
}
