package com.khack.review.tools.verify;

import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionStore;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.json.Json;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConnectorCheckCli {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: ./gradlew -q connectorCheck -Pargs=\"<script.json> [sessionId]\"");
            System.exit(1);
        }
        ConversationScript script = ConversationScript.load(Path.of(args[0]));
        SessionStore store = new SessionStore(Path.of(System.getProperty("review.sessions-file", "data/sessions.jsonl")), Clock.systemUTC());
        Optional<SavedSession> session = args.length > 1
                ? store.list().stream().filter(s -> s.id().equals(args[1])).findFirst()
                : store.latest();
        if (session.isEmpty()) {
            System.err.println("No saved connector session found.");
            System.exit(1);
        }

        List<String> actual = session.get().userTurns().stream()
                .sorted(Comparator.comparingInt(UserTurn::index))
                .map(UserTurn::text)
                .toList();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("script", script.name());
        report.put("sessionId", session.get().id());
        report.put("comparison", TurnComparator.compare(script.userTurns(), actual));
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
