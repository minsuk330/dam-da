package com.khack.review.tools.verify;

import com.khack.review.common.json.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShareProbeCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: ./gradlew -q shareProbe -Pargs=\"<script.json>\"");
            System.exit(1);
        }
        ConversationScript script = ConversationScript.load(Path.of(args[0]));
        if (!script.hasShareUrl()) {
            System.err.println(args[0] + ": shareUrl is empty");
            System.exit(1);
        }

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(script.shareUrl()))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36")
                .header("Accept-Language", "ko,en;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        Path out = Path.of("data/probe", script.name() + ".html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, response.body());

        Map<String, String> headers = new HashMap<>();
        response.headers().map().forEach((k, v) -> headers.put(k, String.join(",", v)));
        ProbeReport report = ShareProbe.analyze(response.statusCode(), headers, response.body(),
                ShareProbe.pickNeedles(script.userTurns()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("script", script.name());
        result.put("url", script.shareUrl());
        result.put("report", report);
        System.out.println(Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
