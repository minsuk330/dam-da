package com.khack.review.tools.verify;

import com.khack.review.collection.adapter.out.playwright.ShareExtractor;
import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareLinkPolicy;
import com.khack.review.collection.domain.ShareStatus;
import com.khack.review.collection.domain.ShareTurn;
import com.khack.review.common.json.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShareVerifyCli {

    public static void main(String[] args) throws Exception {
        String scriptPath = Arrays.stream(args).filter(a -> !a.startsWith("--")).findFirst().orElse(null);
        boolean headed = Arrays.asList(args).contains("--headed");
        if (scriptPath == null) {
            System.err.println("usage: ./gradlew -q shareVerify -Pargs=\"<script.json> [--headed]\"");
            System.exit(1);
        }
        ConversationScript script = ConversationScript.load(Path.of(scriptPath));
        if (!script.hasShareUrl()) {
            System.err.println(scriptPath + ": shareUrl is empty");
            System.exit(1);
        }

        ShareExtraction extraction = ShareExtractor.extract(ShareLinkPolicy.requireAllowed(script.shareUrl()), headed, script.name());
        ShareStatus status = ShareStatus.classify(extraction);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("script", script.name());
        report.put("url", script.shareUrl());
        report.put("httpStatus", extraction.httpStatus());
        report.put("status", status);
        report.put("totalTurns", extraction.turns().size());
        report.put("roles", extraction.turns().stream().map(ShareTurn::role).distinct().toList());
        report.put("comparison", status == ShareStatus.OK
                ? TurnComparator.compare(script.userTurns(), extraction.userTurnTexts())
                : null);

        Path out = Path.of("data/results", script.name() + ".json");
        Files.createDirectories(out.getParent());
        Map<String, Object> full = new LinkedHashMap<>(report);
        full.put("extraction", extraction);
        Files.writeString(out, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(full));
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
