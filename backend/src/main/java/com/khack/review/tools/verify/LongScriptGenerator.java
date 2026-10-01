package com.khack.review.tools.verify;

import com.khack.review.common.json.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LongScriptGenerator {

    private static final List<String> TOPICS = List.of("TCP", "UDP", "HTTP/2", "TLS", "DNS", "CDN", "로드 밸런서", "WebSocket", "QUIC", "NAT");
    private static final List<String> FOLLOW_UPS = List.of("가 정확히 뭐야?", " 예시 하나만 들어줘", "랑 앞에서 말한 거랑 뭐가 달라?");

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "c3-long";
        String platform = args.length > 1 ? args[1] : "claude";

        List<String> turns = new ArrayList<>();
        for (int i = 0; i < TOPICS.size(); i++) {
            for (int j = 0; j < FOLLOW_UPS.size(); j++) {
                turns.add("%d번 질문: %s%s".formatted(i * 3 + j + 1, TOPICS.get(i), FOLLOW_UPS.get(j)));
            }
        }
        if (platform.equals("claude")) {
            turns.add("복습에 넣어줘");
        }

        Path out = Path.of("fixtures/scripts", name + ".json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, Json.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(new ConversationScript(name, platform, turns, "")) + "\n");
        System.out.println(out + ": " + turns.size() + " turns");
    }
}
