package com.khack.review.tools.question;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionStore;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.adapter.out.openai.OpenAiLlmAdapter;
import com.khack.review.common.application.port.out.LlmPort;
import com.khack.review.common.json.Json;
import com.khack.review.question.application.QuestionDrafter;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.PointKind;
import com.khack.review.question.domain.QuestionPlan;
import com.khack.review.question.domain.QuestionPlanner;
import com.khack.review.question.domain.QuestionSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 실제 LLM으로 문제 후보를 만들어 출력한다. DB에 저장하지 않는다. 프롬프트를 바꿀 때 결과를 눈으로 보는 용도다.
 * 키와 모델은 backend/.env의 OPENAI_API_KEY, OPENAI_MODEL.
 *
 * <pre>
 * ./gradlew -q questionGen -Pargs="fixtures/sessions/b2-etag.json CORRECT_MISCONCEPTION,UNDERSTAND_PRINCIPLE"
 * ./gradlew -q questionGen -Pargs="latest REMEMBER_CORE"        (data/sessions.jsonl의 마지막 세션, 또는 세션 ID)
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
        output.put("sessionId", source.sessionId());
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
            SessionInput input = Json.MAPPER.readValue(Files.readString(file), SessionInput.class);
            return source(file.getFileName().toString(), input.userTurns(), input.reviewUnits());
        }
        SessionStore store = new SessionStore(
                Path.of(System.getProperty("review.sessions-file", "data/sessions.jsonl")), Clock.systemUTC());
        SavedSession session = (arg.equals("latest") ? store.latest()
                : store.list().stream().filter(s -> s.id().equals(arg)).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다: " + arg));
        return source(session.id(), session.userTurns(), session.reviewUnits());
    }

    /** 커넥터 스키마(v5)를 문제 생성 입력으로 옮긴다. 헷갈린 지점의 교정은 해당 발화의 correction이다. */
    static QuestionSource source(String sessionId, List<UserTurn> userTurns, List<ReviewUnit> reviewUnits) {
        Map<Integer, UserTurn> byIndex = userTurns.stream()
                .collect(Collectors.toMap(UserTurn::index, Function.identity(), (a, b) -> a));
        List<QuestionSource.Turn> turns = userTurns.stream()
                .map(t -> new QuestionSource.Turn(t.index(), t.text(), t.intent() == Intent.meta))
                .toList();
        List<QuestionSource.Unit> units = reviewUnits.stream()
                .map(unit -> new QuestionSource.Unit(
                        unit.title(),
                        unit.keyPoints().stream()
                                .map(p -> new QuestionSource.KeyPoint(p.point(),
                                        PointKind.valueOf(p.effectiveKind().name().toUpperCase()), p.turns()))
                                .toList(),
                        unit.confusions().stream()
                                .map(c -> new QuestionSource.Confusion(c.turn(), c.userBelief(), correction(byIndex.get(c.turn()))))
                                .toList()))
                .toList();
        return new QuestionSource(sessionId, turns, units);
    }

    private static String correction(UserTurn turn) {
        if (turn == null) {
            return null;
        }
        AiVerdict verdict = turn.effectiveVerdict();
        return verdict == AiVerdict.partial || verdict == AiVerdict.corrected ? turn.correction() : null;
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
