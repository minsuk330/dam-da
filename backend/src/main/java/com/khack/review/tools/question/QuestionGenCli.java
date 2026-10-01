package com.khack.review.tools.question;

import com.khack.review.analysis.application.SessionContent;
import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.json.Json;
import com.khack.review.question.application.QuestionDrafter;
import com.khack.review.question.application.QuestionSources;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.QuestionPlan;
import com.khack.review.question.domain.QuestionPlanner;
import com.khack.review.question.domain.QuestionSource;
import com.khack.review.tools.verify.OpenAiCli;
import com.khack.review.tools.verify.SessionSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: ./gradlew -q questionGen -Pargs=\"<session.json|sessionId|latest> <GOAL[,GOAL]> [--plan] [--out <file>]\"");
            System.err.println("goals: " + Arrays.toString(LearningGoal.values()));
            System.exit(1);
        }
        QuestionSource source = load(args[0]);
        List<LearningGoal> goals = Arrays.stream(args[1].split(",")).map(String::strip).map(LearningGoal::valueOf).toList();
        List<String> options = List.of(args).subList(2, args.length);

        QuestionPlan plan = QuestionPlanner.plan(source, goals);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("source", args[0]);
        output.put("plan", plan);
        if (!options.contains("--plan")) {
            output.put("model", OpenAiCli.model());
            QuestionDrafter.Result result = new QuestionDrafter(OpenAiCli.llm()).draft(source, plan.targets());
            output.put("drafts", result.drafts());
            output.put("failures", result.failures());
        }
        OpenAiCli.print(output, options);
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
}
