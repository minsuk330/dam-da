package com.khack.review.tools.verify;

import com.khack.review.common.adapter.out.typesafe.TypeSafeJevAdapter;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.practice.application.NextActionQuestions;
import com.khack.review.practice.application.NextActionState;
import com.khack.review.practice.application.NextActionState.Step;
import com.khack.review.practice.domain.AttemptOutcome;
import com.khack.review.practice.domain.FeedbackAction;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.question.domain.QuestionType;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.springframework.web.client.RestClient;

/**
 * 다음 행동 선택 질문({@link NextActionQuestions})으로 실제 Jev를 호출해 고른 행동·신뢰도와 기대를 나란히 출력한다.
 * 선택지는 상태 규칙({@code FeedbackRules})이 둘 이상을 허용하는 경우만 다룬다(하나면 Jev를 부르지 않는다).
 * 질문 문구를 바꿀 때 실제 분포를 보는 용도다. 키는 backend/.env의 TYPESAFE_API_KEY.
 * 사용: ./gradlew -q nextActionCheck -Pargs="--out build/next-action.json"
 */
public final class NextActionCheckCli {

    /** 신뢰도가 이보다 낮으면 서버는 규칙의 기본 행동을 쓴다(review.practice.feedback.min-confidence). */
    private static final double MIN_CONFIDENCE = 0.5;

    private static final String STEM = "InnoDB에서 일반 SELECT는 행 락을 걸지 않고 무엇을 읽어 결과를 돌려주나요?";

    private record Sample(String name, NextActionState state, Set<FeedbackAction> allowed, FeedbackAction expected) {
    }

    private static NextActionState state(PracticeKind kind, AttemptOutcome latest, List<Step> attempts, boolean hint,
            boolean explanation, boolean repeated) {
        return new NextActionState(kind, false, QuestionType.SHORT_ANSWER, STEM, latest, attempts, hint, explanation, repeated);
    }

    private static List<Sample> samples() {
        Step wrong = new Step(AttemptKind.FIRST_UNASSISTED, AttemptOutcome.WRONG);
        Step retryCorrect = new Step(AttemptKind.ASSISTED_RETRY, AttemptOutcome.CORRECT);
        Set<FeedbackAction> hintOrExplain = EnumSet.of(FeedbackAction.GIVE_HINT, FeedbackAction.EXPLAIN_CONCEPT);
        Set<FeedbackAction> advanceOrRelearn = EnumSet.of(FeedbackAction.ADVANCE, FeedbackAction.RELEARN_TODAY);
        Set<FeedbackAction> relearnOrExplain = EnumSet.of(FeedbackAction.RELEARN_TODAY, FeedbackAction.EXPLAIN_CONCEPT);
        Set<FeedbackAction> explainOrAdvance = EnumSet.of(FeedbackAction.EXPLAIN_CONCEPT, FeedbackAction.ADVANCE);

        List<Sample> samples = new ArrayList<>();
        samples.add(new Sample("첫 학습 · 처음 틀림", state(PracticeKind.FIRST_STUDY, AttemptOutcome.WRONG, List.of(wrong),
                false, false, false), hintOrExplain, FeedbackAction.GIVE_HINT));
        samples.add(new Sample("첫 학습 · 틀림 · 여러 번 틀려 온 항목", state(PracticeKind.FIRST_STUDY, AttemptOutcome.WRONG,
                List.of(wrong), false, false, true), hintOrExplain, FeedbackAction.EXPLAIN_CONCEPT));
        samples.add(new Sample("첫 학습 · 힌트 보고 맞힘", state(PracticeKind.FIRST_STUDY, AttemptOutcome.CORRECT,
                List.of(wrong, retryCorrect), true, false, false), advanceOrRelearn, FeedbackAction.ADVANCE));
        samples.add(new Sample("첫 학습 · 힌트 보고 맞힘 · 여러 번 틀려 온 항목", state(PracticeKind.FIRST_STUDY, AttemptOutcome.CORRECT,
                List.of(wrong, retryCorrect), true, false, true), advanceOrRelearn, FeedbackAction.RELEARN_TODAY));
        samples.add(new Sample("매일 학습 · 설명 보고 맞힘", state(PracticeKind.DAILY, AttemptOutcome.CORRECT,
                List.of(wrong, retryCorrect), false, true, false), advanceOrRelearn, FeedbackAction.RELEARN_TODAY));
        samples.add(new Sample("매일 학습 · 처음 틀림", state(PracticeKind.DAILY, AttemptOutcome.WRONG, List.of(wrong),
                false, false, false), relearnOrExplain, FeedbackAction.RELEARN_TODAY));
        samples.add(new Sample("매일 학습 · 틀림 · 여러 번 틀려 온 항목", state(PracticeKind.DAILY, AttemptOutcome.WRONG,
                List.of(wrong), false, false, true), relearnOrExplain, FeedbackAction.EXPLAIN_CONCEPT));
        samples.add(new Sample("매일 학습 · 틀림 · 다시 묻기 상한에 닿음", state(PracticeKind.DAILY, AttemptOutcome.WRONG,
                List.of(wrong), false, false, false), explainOrAdvance, FeedbackAction.EXPLAIN_CONCEPT));
        return samples;
    }

    public static void main(String[] args) throws IOException {
        String apiKey = loadApiKey();
        if (apiKey.isBlank()) {
            System.err.println("TYPESAFE_API_KEY가 없습니다. backend/.env에 넣으세요.");
            System.exit(1);
        }
        JevPort jev = new TypeSafeJevAdapter(RestClient.builder(), apiKey, "https://api.typesafe.ai", "jev-latest");
        List<Map<String, Object>> rows = new ArrayList<>();
        int matched = 0;
        for (Sample sample : samples()) {
            JevResult result = jev.evaluate(sample.state(), NextActionQuestions.questions(sample.allowed()));
            JevAnswer.Choice choice = result.choice(NextActionQuestions.NEXT_ACTION);
            boolean confident = choice.confidence() >= MIN_CONFIDENCE;
            boolean match = confident && NextActionQuestions.optionName(sample.expected()).equals(choice.choice());
            matched += match ? 1 : 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sample", sample.name());
            row.put("allowed", sample.allowed());
            row.put("expected", sample.expected());
            row.put("chosen", choice.choice());
            row.put("confidence", choice.confidence());
            row.put("usedByServer", confident);
            row.put("match", match);
            rows.add(row);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("matched", matched + "/" + rows.size());
        report.put("minConfidence", MIN_CONFIDENCE);
        report.put("samples", rows);
        OpenAiCli.print(report, List.of(args));
    }

    private static String loadApiKey() throws IOException {
        Path env = Path.of(".env");
        if (Files.exists(env)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(env)) {
                properties.load(reader);
            }
            String key = properties.getProperty("TYPESAFE_API_KEY", "").strip();
            if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            if (!key.isBlank()) {
                return key;
            }
        }
        return System.getenv().getOrDefault("TYPESAFE_API_KEY", "");
    }
}
