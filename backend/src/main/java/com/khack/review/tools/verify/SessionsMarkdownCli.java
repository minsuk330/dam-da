package com.khack.review.tools.verify;

import com.khack.review.collection.adapter.in.web.dev.SessionMarkdown;
import com.khack.review.collection.domain.SessionStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

public final class SessionsMarkdownCli {

    public static void main(String[] args) throws Exception {
        SessionStore store = new SessionStore(Path.of(System.getProperty("review.sessions-file", "data/sessions.jsonl")), Clock.systemUTC());
        Path out = Path.of(args.length > 0 ? args[0] : "data/sessions.md");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, SessionMarkdown.render(store.list()));
        System.out.println(out.toAbsolutePath() + ": " + store.list().size() + " sessions");
    }
}
