package com.khack.review.collection.adapter.in.web.dev;

import com.khack.review.collection.domain.SessionStore;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only viewer for sessions received through the connector. The server is exposed through a
 * tunnel during verification, so anything that did not come straight from this machine gets a 404.
 */
@RestController
class DevSessionsController {

    private static final Set<String> LOCAL_HOSTNAMES = Set.of("localhost", "127.0.0.1", "[::1]");
    private static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final SessionStore store;

    DevSessionsController(SessionStore store) {
        this.store = store;
    }

    @GetMapping("/dev/sessions")
    ResponseEntity<String> page(HttpServletRequest request) {
        if (!isLocal(request)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(SessionPage.render(store.list()));
    }

    @GetMapping("/dev/sessions.md")
    ResponseEntity<String> markdown(HttpServletRequest request) {
        if (!isLocal(request)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MARKDOWN).body(SessionMarkdown.render(store.list()));
    }

    private static boolean isLocal(HttpServletRequest request) {
        if (request.getHeader("X-Forwarded-For") != null || request.getHeader("Forwarded") != null) {
            return false;
        }
        String host = request.getServerName();
        if (!LOCAL_HOSTNAMES.contains(host)) {
            return false;
        }
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
