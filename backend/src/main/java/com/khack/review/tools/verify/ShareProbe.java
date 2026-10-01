package com.khack.review.tools.verify;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ShareProbe {

    private ShareProbe() {
    }

    public static List<String> pickNeedles(List<String> userTurns) {
        List<String> ends = userTurns.size() > 1
                ? List.of(userTurns.get(0), userTurns.get(userTurns.size() - 1))
                : List.of(userTurns.get(0));
        return ends.stream().map(t -> {
            String s = t.strip();
            return s.substring(0, Math.min(20, s.length())).strip();
        }).toList();
    }

    public static ProbeReport analyze(int status, Map<String, String> headers, String body, List<String> needles) {
        Map<String, String> h = new HashMap<>();
        headers.forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v));
        String lowerBody = body.toLowerCase(Locale.ROOT);

        List<String> found = needles.stream()
                .filter(n -> variants(n).stream().anyMatch(v -> body.contains(v) || lowerBody.contains(v.toLowerCase(Locale.ROOT))))
                .toList();
        List<String> missing = needles.stream().filter(n -> !found.contains(n)).toList();

        return new ProbeReport(
                status,
                h.getOrDefault("content-type", ""),
                body.getBytes(StandardCharsets.UTF_8).length,
                h.containsKey("cf-ray") || h.getOrDefault("server", "").contains("cloudflare"),
                body.contains("data-message-author-role"),
                found,
                missing,
                status == 200 && missing.isEmpty());
    }

    private static List<String> variants(String needle) {
        String json = needle.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        StringBuilder unicode = new StringBuilder();
        for (char c : json.toCharArray()) {
            unicode.append(c > 0x7F ? "\\u%04x".formatted((int) c) : String.valueOf(c));
        }
        String html = needle.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        return List.of(needle, json, unicode.toString(), html);
    }
}
