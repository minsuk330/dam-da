package com.khack.review.tools.verify;

import com.khack.review.common.adapter.out.openai.OpenAiLlmAdapter;
import com.khack.review.common.application.port.out.LlmPort;
import com.khack.review.common.json.Json;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 실제 LLM을 부르는 tools CLI의 공통 부분. 키와 모델은 backend/.env의 OPENAI_API_KEY, OPENAI_MODEL에서 읽는다.
 */
public final class OpenAiCli {

    public static final String DEFAULT_MODEL = "gpt-6-luna";

    private OpenAiCli() {
    }

    public static String model() throws IOException {
        return value("OPENAI_MODEL", DEFAULT_MODEL);
    }

    /** 키가 없으면 안내하고 종료한다. */
    public static LlmPort llm() throws IOException {
        String apiKey = value("OPENAI_API_KEY", "");
        if (apiKey.isBlank()) {
            System.err.println("OPENAI_API_KEY가 없습니다. backend/.env에 넣으세요.");
            System.exit(1);
        }
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().apiKey(apiKey).model(model()).build())
                .build();
        return new OpenAiLlmAdapter(ChatClient.builder(chatModel));
    }

    /** {@code --out <file>}이 있으면 UTF-8 파일로, 없으면 표준 출력으로 낸다. 콘솔에서 한글이 깨질 때 파일로 받는다. */
    public static void print(Object output, List<String> options) throws IOException {
        String json = Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        int out = options.indexOf("--out");
        if (out < 0) {
            System.out.println(json);
            return;
        }
        if (out + 1 >= options.size()) {
            throw new IllegalArgumentException("--out 뒤에 파일 경로가 필요합니다.");
        }
        Path file = Path.of(options.get(out + 1)).toAbsolutePath();
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
        System.out.println("wrote " + file);
    }

    private static String value(String name, String fallback) throws IOException {
        Properties env = new Properties();
        Path file = Path.of(".env");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                env.load(reader);
            }
        }
        String value = env.getProperty(name, "").strip();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isBlank()) {
            value = System.getenv().getOrDefault(name, "");
        }
        return value.isBlank() ? fallback : value;
    }
}
