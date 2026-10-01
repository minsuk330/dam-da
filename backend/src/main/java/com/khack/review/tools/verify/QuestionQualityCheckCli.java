package com.khack.review.tools.verify;

import com.khack.review.common.adapter.out.typesafe.TypeSafeJevAdapter;
import com.khack.review.common.application.port.out.JevPort;
import com.khack.review.common.application.port.out.JevResult;
import com.khack.review.common.json.Json;
import com.khack.review.question.application.QualityOutcome;
import com.khack.review.question.application.QuestionQualityJudge;
import com.khack.review.question.application.QuestionQualityPolicy;
import com.khack.review.question.application.QuestionQualityQuestions;
import com.khack.review.question.application.QuestionQualityState;
import com.khack.review.question.application.port.out.UnitQuestionRequest.EvidenceTurn;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.web.client.RestClient;

/**
 * 문제 품질 검사 질문({@link QuestionQualityQuestions})으로 실제 Jev를 호출해 원 답과 승인 여부를 출력한다.
 * 질문이나 기준을 바꿀 때 실제 확률·신뢰도 분포를 보는 용도다. 키는 backend/.env의 TYPESAFE_API_KEY.
 * 사용: ./gradlew -q questionQualityCheck -Pargs="--out build/question-quality.json"
 */
public final class QuestionQualityCheckCli {

    /** application.yml의 review.question 기본값과 같게 유지한다. */
    private static final QuestionQualityPolicy POLICY = new QuestionQualityPolicy(0.7, 0.5, 0.5, 0.7, 3, Duration.ofSeconds(1), 3);

    private record Sample(String name, boolean expectApproved, QuestionQualityState state) {
    }

    public static void main(String[] args) throws IOException {
        String apiKey = loadApiKey();
        if (apiKey.isBlank()) {
            System.err.println("TYPESAFE_API_KEY가 없습니다. backend/.env에 넣으세요.");
            System.exit(1);
        }
        JevPort adapter = new TypeSafeJevAdapter(RestClient.builder(), apiKey, "https://api.typesafe.ai", "jev-latest");
        List<JevResult> raw = new ArrayList<>();
        JevPort recording = (state, questions) -> {
            JevResult result = adapter.evaluate(state, questions);
            raw.add(result);
            return result;
        };
        QuestionQualityJudge judge = new QuestionQualityJudge(recording, POLICY);

        List<Map<String, Object>> output = new ArrayList<>();
        int matched = 0;
        for (Sample sample : samples()) {
            raw.clear();
            QualityOutcome outcome = judge.judge(sample.state());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sample", sample.name());
            row.put("expectApproved", sample.expectApproved());
            row.put("approved", outcome.approved());
            row.put("note", outcome.note());
            row.put("answers", raw.isEmpty() ? null : raw.get(raw.size() - 1).answers());
            output.add(row);
            if (outcome.approved() == sample.expectApproved()) {
                matched++;
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("matched", matched + "/" + output.size());
        report.put("samples", output);
        OpenAiCli.print(report, List.of(args));
    }

    private static final String SELECT_FACT = "일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다";
    private static final String SCAN_WARNING = "FOR UPDATE를 인덱스 없이 쓰면 스캔한 범위 전체가 잠긴다";
    private static final List<EvidenceTurn> SELECT_EVIDENCE = List.of(new EvidenceTurn(1, "InnoDB에서 일반 SELECT도 락을 걸어?", null, null));
    private static final List<EvidenceTurn> SCAN_EVIDENCE = List.of(new EvidenceTurn(2,
            "그럼 FOR UPDATE는 스캔 다 하고 나서 락 거는 거 아니야?", "corrected", "스캔하면서 읽는 레코드마다 즉시 잠근다"));

    private static List<Sample> samples() {
        List<Sample> samples = new ArrayList<>();
        samples.add(new Sample("통과: 원리 서술 (핵심 사실, 교정 없음)", true, new QuestionQualityState(SELECT_FACT, "FACT", null,
                SELECT_EVIDENCE, "ESSAY",
                "InnoDB에서 일반 SELECT가 데이터를 읽을 때 잠금을 걸지 않는 이유와 그 읽기 방식이 어떻게 작동하는지 설명하세요.", List.of(), null,
                List.of("일반 SELECT는 MVCC 스냅샷을 읽는다고 설명한다.", "일반 SELECT는 읽을 때 락을 걸지 않는다고 설명한다."), List.of())));
        samples.add(new Sample("통과: 단답 (핵심 사실)", true, new QuestionQualityState(SELECT_FACT, "FACT", null,
                SELECT_EVIDENCE, "SHORT_ANSWER", "InnoDB의 일반 SELECT는 행 락 없이 무엇을 읽어 결과를 돌려주나요?", List.of(), null,
                List.of("MVCC 스냅샷을 읽는다."), List.of())));
        samples.add(new Sample("통과: 사례 판단 (경고, 새 상황)", true, new QuestionQualityState(SCAN_WARNING, "WARNING", null,
                SCAN_EVIDENCE, "CASE_JUDGMENT",
                "한 트랜잭션이 인덱스가 없는 조건으로 SELECT ... FOR UPDATE를 실행한다. 잠금은 조건에 맞는 레코드에만 걸리는가, 스캔한 범위 전체에 걸리는가? 근거도 설명하세요.",
                List.of(), null, List.of("인덱스 없이 FOR UPDATE를 쓰면 스캔한 범위 전체가 잠긴다."), List.of())));
        samples.add(new Sample("통과: 오류 찾기 (헷갈린 지점)", true, new QuestionQualityState("스캔을 모두 마친 뒤 한꺼번에 락을 건다", "CONFUSION",
                "스캔하면서 읽는 레코드마다 즉시 잠근다", SCAN_EVIDENCE, "ERROR_FINDING",
                "한 학습자가 \"FOR UPDATE는 검색할 레코드를 모두 훑은 다음에야 잠금을 건다\"고 설명했습니다. 잘못된 부분을 찾아 올바른 잠금 시점을 말해 보세요.",
                List.of(), null, List.of("스캔을 마친 뒤 한꺼번에 잠그는 것이 아니라고 지적한다.", "스캔하면서 읽는 레코드마다 즉시 잠근다고 설명한다."), List.of())));
        samples.add(new Sample("통과: 객관식", true, new QuestionQualityState(SELECT_FACT, "FACT", null, SELECT_EVIDENCE,
                "MULTIPLE_CHOICE", "InnoDB에서 일반 SELECT의 읽기 방식으로 옳은 것은?",
                List.of("행마다 공유 락을 걸고 읽는다", "MVCC 스냅샷을 읽고 락을 걸지 않는다", "테이블 전체에 배타 락을 건다", "갭 락만 걸고 읽는다"), 1,
                List.of("일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다."), List.of())));
        samples.add(new Sample("탈락: 대화에 없는 사실을 정답으로 요구", false, new QuestionQualityState(SELECT_FACT, "FACT", null,
                SELECT_EVIDENCE, "SHORT_ANSWER", "InnoDB의 기본 트랜잭션 격리 수준과 undo 로그가 저장되는 테이블스페이스 이름은 무엇인가요?",
                List.of(), null, List.of("기본 격리 수준은 REPEATABLE READ다.", "undo 로그는 undo 테이블스페이스에 저장된다."), List.of())));
        samples.add(new Sample("탈락: 정답 기준이 항목과 반대", false, new QuestionQualityState(SELECT_FACT, "FACT", null,
                SELECT_EVIDENCE, "SHORT_ANSWER", "InnoDB에서 일반 SELECT는 읽는 행에 어떤 락을 거나요?", List.of(), null,
                List.of("일반 SELECT는 읽는 행마다 공유 락을 건다."), List.of())));
        samples.add(new Sample("탈락: 모호한 문제", false, new QuestionQualityState(SELECT_FACT, "FACT", null,
                SELECT_EVIDENCE, "ESSAY", "InnoDB에 대해 아는 것을 적절히 설명해 보세요.", List.of(), null,
                List.of("일반 SELECT는 MVCC 스냅샷을 읽는다."), List.of())));
        samples.add(new Sample("탈락: 기존 문제와 중복", false, new QuestionQualityState(SELECT_FACT, "FACT", null,
                SELECT_EVIDENCE, "SHORT_ANSWER", "InnoDB의 일반 SELECT는 락 없이 무엇을 읽어서 결과를 돌려주나요?", List.of(), null,
                List.of("MVCC 스냅샷을 읽는다."), List.of("InnoDB의 일반 SELECT는 행 락 없이 무엇을 읽어 결과를 돌려주나요?"))));
        return samples;
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
