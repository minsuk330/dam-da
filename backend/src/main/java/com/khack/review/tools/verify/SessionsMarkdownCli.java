package com.khack.review.tools.verify;

import com.khack.review.collection.adapter.in.web.dev.SessionMarkdown;
import com.khack.review.collection.domain.SavedSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SessionsMarkdownCli {

    public static void main(String[] args) throws Exception {
        List<SavedSession> sessions = SessionSource.list();
        Path out = Path.of(args.length > 0 ? args[0] : "data/sessions.md");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, SessionMarkdown.render(sessions));
        System.out.println(out.toAbsolutePath() + ": " + sessions.size() + " sessions");
    }
}
