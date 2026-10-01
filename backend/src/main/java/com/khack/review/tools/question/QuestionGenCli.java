package com.khack.review.tools.question;

import com.khack.review.analysis.application.SessionContent;
import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.adapter.out.openai.OpenAiLlmAdapter;
import com.khack.review.common.application.port.out.LlmPort;
import com.khack.review.common.json.Json;
import com.khack.review.question.application.QuestionDrafter;
import com.khack.review.question.application.QuestionSources;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.QuestionPlan;
import com.khack.review.question.domain.QuestionPlanner;
import com.khack.review.question.domain.QuestionSource;
import com.khack.review.tools.verify.SessionSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 실제 LLM으로 문제 후보를 만들어 출력한다. DB에 저장하지 않는다. 프롬프트를 바꿀 때 결과를 눈으로 보는 용도다.
 * 키와 모델은 backend/.env의 OPENAI_API_KEY, OPENAI_MODEL.
 *
 * <pre>
 * ./gradlew -q questionGen -Pargs="fixtures/sessions/b2-etag.json CORRECT_MISCONCEPTION,UNDERSTAND_PRINCIPLE"
 * ./gradlew -q questionGen -Pargs="latest REMEMBER_CORE"        (실행 중인 서버에 저장된 마지막 대화, 또는 대화 ID. -Pserver로 서버 지정)
 * ./gradlew -q questionGen -Pargs="fixtures/sessions/b2-etag.json REMEMBER_CORE --plan"   (LLM 호출 없이 출제 계획만)
 * ./gradlew -q questionGen -Pargs="... --out build/question-gen.json"   (UTF-8 파일로 저장. 콘솔에서 한글이 깨질 때)
 * </pre>
 */
public final class QuestionGenCli {

    private static final String DEFAULT_MODEL = "gpt-6-luna";

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: ./gradlew -q questionGen -Pargs=\"<session.json|sessionId|latest> <GOAL[,GOAL]> [--plan] [--out <file>]\"");
            System.err.println("goals: " + Arrays.toString(LearningGoal.values()));
            System.exit(1);
        }
        QuestionSource source = load(args[0]);
        List<LearningGoal> goals = Arrays.stream(args[1].split(",")).map(String::strip).map(LearningGoal::valueOf).toList();
        List<String> options = List.of(args).subList(2, args.length);
        boolean planOnly = options.contains("--plan");
        int out = options.indexOf("--out");
        if (out >= 0 && out + 1 >= options.size()) {
            System.err.println("--out 뒤에 파일 경로가 필요합니다.");
            System.exit(1);
        }

        QuestionPlan plan = QuestionPlanner.plan(source, goals);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("source", args[0]);
        output.put("plan", plan);
        if (!planOnly) {
            Properties env = loadEnv();
            String apiKey = value(env, "OPENAI_API_KEY", "");
            if (apiKey.isBlank()) {
                System.err.println("OPENAI_API_KEY가 없습니다. backend/.env에 넣으세요.");
                System.exit(1);
            }
            String model = value(env, "OPENAI_MODEL", DEFAULT_MODEL);
            output.put("model", model);
            QuestionDrafter.Result result = new QuestionDrafter(llm(apiKey, model)).draft(source, plan.targets());
            output.put("drafts", result.drafts());
            output.put("failures", result.failures());
        }
        String json = Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        if (out >= 0) {
            Path file = Path.of(options.get(out + 1)).toAbsolutePath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
            System.out.println("wrote " + file);
        } else {
            System.out.println(json);
        }
    }

    private static LlmPort llm(String apiKey, String model) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder().apiKey(apiKey).model(model).build())
                .build();
        return new OpenAiLlmAdapter(ChatClient.builder(chatModel));
    }

    private static QuestionSource load(String arg) throws IOException {
        Path file = Path.of(arg);
        if (Files.isRegularFile(file)) {
            return source(Json.MAPPER.readValue(Files.readString(file), SessionInput.class));
        }
        String[] id = arg.equals("latest") ? new String[0] : new String[] {arg};
        SavedSession session = SessionSource.find(id, 0)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + arg));
        return source(new SessionInput(session.userTurns(), session.reviewUnits(), session.topicHint()));
    }

    /** 서버와 같은 경로로 옮긴다: 커넥터 스키마(v5) → 학습 세션·기억 항목 → 문제 생성 입력. 저장하지 않아 ID는 없다. */
    static QuestionSource source(SessionInput input) {
        LearningSession session = LearningSession.create(0L, 0L, input, Instant.EPOCH);
        return QuestionSources.of(SessionContent.of(session), input.userTurns());
    }

    private static Properties loadEnv() throws IOException {
        Properties properties = new Properties();
        Path env = Path.of(".env");
        if (Files.exists(env)) {
            try (Reader reader = Files.newBufferedReader(env)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    private static String value(Properties env, String name, String fallback) {
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
