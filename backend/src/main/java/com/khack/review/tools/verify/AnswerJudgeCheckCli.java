package com.khack.review.tools.verify;

import com.khack.review.common.adapter.out.typesafe.TypeSafeJevAdapter;
import com.khack.review.common.application.port.out.JevAnswer;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.practice.application.AnswerJudgeQuestions;
import com.khack.review.practice.application.AnswerJudgeState;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.web.client.RestClient;

/**
 * 답변 판정 질문({@link AnswerJudgeQuestions})으로 실제 Jev를 호출해 원 답(확률·신뢰도)과 기대를 나란히 출력한다.
 * 질문 문구를 바꿀 때 실제 분포를 보는 용도다. 키는 backend/.env의 TYPESAFE_API_KEY.
 * 사용: ./gradlew -q answerJudgeCheck -Pargs="--out build/answer-judge.json"
 */
public final class AnswerJudgeCheckCli {

    /** 기대. {@code verdicts}는 허용하는 판정(모호한 경우는 둘 다 허용), 나머지는 null이면 따지지 않는다. */
    private record Sample(String name, AnswerJudgeState state, List<String> verdicts, Boolean omission, Boolean contradiction,
            Boolean offTarget, Boolean repeatsUserBelief, String misread) {

        Sample(String name, AnswerJudgeState state, List<String> verdicts, Boolean omission, Boolean contradiction,
                Boolean offTarget, Boolean repeatsUserBelief) {
            this(name, state, verdicts, omission, contradiction, offTarget, repeatsUserBelief, AnswerJudgeQuestions.AS_ASKED);
        }
    }

    private static final String Q_SELECT = "InnoDB에서 일반 SELECT는 행 락을 걸지 않고 무엇을 읽어 결과를 돌려주나요?";
    private static final List<String> C_SELECT = List.of("MVCC 스냅샷(트랜잭션 시점의 일관된 읽기 뷰)을 읽는다");
    private static final String M_SELECT = "MVCC 스냅샷을 읽는다. 변경된 행은 undo 로그로 이전 버전을 복원해 읽는다.";
    private static final String ITEM_SELECT = "일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다";

    private static final String Q_TWO = "InnoDB에서 일반 SELECT가 락을 걸지 않는 이유와 그때 읽는 대상을 설명하세요.";
    private static final List<String> C_TWO = List.of("MVCC 스냅샷을 읽는다", "그래서 행에 락을 걸지 않는다");

    private static final String Q_BELIEF = "다음 주장에서 틀린 곳을 찾아 바르게 고치세요: \"SELECT ... FOR UPDATE는 스캔을 모두 마친 뒤 한꺼번에 락을 건다.\"";
    private static final List<String> C_BELIEF = List.of("스캔하면서 읽는 레코드마다 즉시 배타 락을 건다고 고친다");
    private static final String M_BELIEF = "스캔을 마친 뒤가 아니라, 스캔하면서 읽는 레코드마다 즉시 배타 락을 건다.";
    private static final String BELIEF = "스캔을 모두 마친 뒤 한꺼번에 락을 건다";
    private static final String CORRECTION = "스캔하면서 읽는 레코드마다 즉시 잠근다";

    private static AnswerJudgeState fact(String question, List<String> criteria, String answer) {
        return new AnswerJudgeState(question, "SHORT_ANSWER", criteria, M_SELECT, answer, ITEM_SELECT, null, null);
    }

    private static AnswerJudgeState belief(String answer) {
        return new AnswerJudgeState(Q_BELIEF, "ERROR_FINDING", C_BELIEF, M_BELIEF, answer, BELIEF, BELIEF, CORRECTION);
    }

    private static List<Sample> samples() {
        List<Sample> samples = new ArrayList<>();
        samples.add(new Sample("met: 표현이 다른 정답", fact(Q_SELECT, C_SELECT, "트랜잭션이 시작된 시점을 기준으로 한 일관된 읽기 뷰를 봐요."),
                List.of("met"), null, null, false, null));
        samples.add(new Sample("met: 모범 답안의 덧붙임(undo 로그) 없이도 기준 충족", fact(Q_SELECT, C_SELECT, "MVCC 스냅샷이요."),
                List.of("met"), null, null, false, null));
        samples.add(new Sample("met + 평가 대상 밖 오류", fact(Q_SELECT, C_SELECT,
                "MVCC 스냅샷을 읽어요. 참고로 InnoDB는 기본적으로 MyISAM처럼 테이블 락을 써요."),
                List.of("met"), null, null, true, null));
        samples.add(new Sample("not_met: 기준 하나만 충족(누락)", fact(Q_TWO, C_TWO, "행에 락을 걸지 않아요."),
                List.of("not_met"), true, false, false, null));
        samples.add(new Sample("not_met: 모르겠다고 답함(분명한 누락)", fact(Q_SELECT, C_SELECT, "모르겠어요."),
                List.of("not_met", "unable_to_judge"), true, false, false, null));
        samples.add(new Sample("not_met: 올바른 내용 뒤 같은 답변에서 번복", fact(Q_SELECT, C_SELECT,
                "MVCC 스냅샷을 읽는다고 배웠는데, 아니에요, 스냅샷이 아니라 항상 가장 최신 값을 직접 읽어요."),
                List.of("not_met"), null, true, false, null));
        samples.add(new Sample("not_met: 틀린 주장(contradiction)", fact(Q_SELECT, C_SELECT, "스냅샷이 아니라 항상 가장 최신 값을 직접 읽어요."),
                List.of("not_met"), null, true, false, null));
        samples.add(new Sample("unable_to_judge: 뜻을 알 수 없는 답", fact(Q_SELECT, C_SELECT, "그게 그러니까 어떻게 보면 그런 식으로 되는 거 아닌가요 ㅎㅎ"),
                List.of("unable_to_judge", "not_met"), null, null, null, null, null));
        samples.add(new Sample("misread: 다른 것을 묻는 줄 알고 답함", fact(Q_SELECT, C_SELECT, "SELECT는 SQL에서 테이블의 데이터를 조회하는 명령어예요."),
                List.of("not_met", "unable_to_judge"), true, false, false, null, AnswerJudgeQuestions.MISREAD_CHOICE));
        samples.add(new Sample("헷갈린 지점: 믿음을 그대로 반복", belief("틀린 곳 없어요. 스캔을 다 끝내고 나서 락을 한 번에 건다고 알고 있어요."),
                List.of("not_met"), null, true, null, true));
        samples.add(new Sample("헷갈린 지점: 다른 틀린 주장", belief("FOR UPDATE는 락을 전혀 걸지 않고 읽기만 해요. 틀린 곳은 없어요."),
                List.of("not_met"), null, true, null, false));
        samples.add(new Sample("헷갈린 지점: 바르게 고침", belief("한꺼번에가 아니라 스캔하면서 읽는 레코드마다 바로 배타 락을 건다."),
                List.of("met"), null, null, false, null));
        samples.add(new Sample("헷갈린 지점: 고친 뒤 믿음으로 번복", belief("스캔하면서 레코드마다 락을 건다. 아니, 다시 생각하니 다 끝내고 한꺼번에 거는 게 맞아요."),
                List.of("not_met"), null, true, null, true));
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
            boolean withBelief = sample.state().userBelief() != null;
            JevResult result = jev.evaluate(sample.state(), AnswerJudgeQuestions.questions(withBelief));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sample", sample.name());
            row.put("answer", sample.state().answer());
            row.put("expectVerdict", sample.verdicts());
            row.put("answers", result.answers());
            List<String> misses = misses(sample, result);
            row.put("misses", misses);
            rows.add(row);
            matched += misses.isEmpty() ? 1 : 0;
            System.out.printf("%s %s%n      %s%n", misses.isEmpty() ? "OK  " : "MISS", sample.name(), summary(result));
            misses.forEach(m -> System.out.println("      ↳ " + m));
        }
        System.out.println("matched " + matched + "/" + rows.size());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("matched", matched + "/" + rows.size());
        report.put("samples", rows);
        OpenAiCli.print(report, List.of(args));
    }

    private static List<String> misses(Sample sample, JevResult result) {
        List<String> misses = new ArrayList<>();
        JevAnswer.Choice verdict = result.choice(AnswerJudgeQuestions.VERDICT);
        if (!sample.verdicts().contains(verdict.choice())) {
            misses.add("verdict " + verdict.choice() + " (기대 " + sample.verdicts() + ")");
        }
        check(misses, "omission", sample.omission(), probability(result, AnswerJudgeQuestions.OMISSION));
        check(misses, "contradiction", sample.contradiction(), probability(result, AnswerJudgeQuestions.CONTRADICTION));
        check(misses, "offTargetError", sample.offTarget(), probability(result, AnswerJudgeQuestions.OFF_TARGET_ERROR));
        check(misses, "repeatsUserBelief", sample.repeatsUserBelief(), probability(result, AnswerJudgeQuestions.REPEATS_USER_BELIEF));
        if (sample.misread() != null && result.answers().get(AnswerJudgeQuestions.MISREAD) instanceof JevAnswer.Choice misread
                && !sample.misread().equals(misread.choice())) {
            misses.add("misread " + misread.choice() + " (기대 " + sample.misread() + ")");
        }
        return misses;
    }

    /** 기대가 참이면 0.7 이상, 거짓이면 0.3 이하여야 한다(그 사이는 애매하다고 본다). */
    private static void check(List<String> misses, String name, Boolean expected, Double probability) {
        if (expected == null || probability == null) {
            return;
        }
        if (expected && probability < 0.7 || !expected && probability > 0.3) {
            misses.add("%s %.2f (기대 %s)".formatted(name, probability, expected ? "≥0.7" : "≤0.3"));
        }
    }

    private static Double probability(JevResult result, String name) {
        JevAnswer answer = result.answers().get(name);
        return answer instanceof JevAnswer.Noul noul ? noul.probability() : null;
    }

    private static String summary(JevResult result) {
        StringBuilder out = new StringBuilder();
        result.answers().forEach((name, answer) -> {
            if (answer instanceof JevAnswer.Choice choice) {
                out.append("%s=%s(신뢰도 %.2f) ".formatted(name, choice.choice(), choice.confidence()));
            } else if (answer instanceof JevAnswer.Noul noul) {
                out.append("%s=%.2f ".formatted(name, noul.probability()));
            }
        });
        return out.toString().strip();
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
