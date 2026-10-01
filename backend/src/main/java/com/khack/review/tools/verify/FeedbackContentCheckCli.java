package com.khack.review.tools.verify;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.practice.adapter.out.llm.LlmFeedbackContentGenerator;
import com.khack.review.practice.application.port.out.FeedbackContentRequest;
import com.khack.review.practice.application.port.out.FeedbackContentRequest.EvidenceTurn;
import com.khack.review.question.domain.QuestionType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실제 LLM으로 힌트·개념 설명·선행 개념 제안을 만들어 출력한다. 피드백 프롬프트를 바꿀 때 결과를 눈으로 보는 용도다.
 * 생성이 거부되면(정답 노출, 길이 초과 등) 그 이유를 적는다. 그 경우 서버는 문제에 저장된 기본 힌트·설명을 쓴다.
 * 키와 모델은 backend/.env. 사용: ./gradlew -q feedbackContentCheck -Pargs="--out build/feedback-content.json"
 */
public final class FeedbackContentCheckCli {

    private record Sample(String name, FeedbackContentRequest request) {
    }

    private static List<Sample> samples() {
        String belief = "스캔을 모두 마친 뒤 한꺼번에 락을 건다";
        String correction = "스캔을 마친 뒤가 아니라 스캔하면서 읽는 레코드마다 즉시 배타 락을 건다";
        List<EvidenceTurn> lockEvidence = List.of(
                new EvidenceTurn(1, "InnoDB에서 SELECT ... FOR UPDATE는 락을 어떻게 걸어?", null, null),
                new EvidenceTurn(2, "그럼 FOR UPDATE는 스캔 다 하고 나서 락 거는 거 아니야?", "corrected", correction));
        List<Sample> samples = new ArrayList<>();
        samples.add(new Sample("헷갈린 지점 · 오류 찾기 · 믿음을 그대로 답함", new FeedbackContentRequest(
                "한 학습자가 \"FOR UPDATE는 스캔을 모두 마친 뒤 한꺼번에 락을 건다\"고 설명했습니다. 틀린 곳을 찾아 바르게 고치세요.",
                QuestionType.ERROR_FINDING, List.of(), List.of("스캔하면서 읽는 레코드마다 즉시 배타 락을 건다고 고친다"),
                "스캔을 마친 뒤가 아니라 스캔하면서 읽는 레코드마다 즉시 배타 락을 건다.", MemoryItemKind.CONFUSION, belief, belief, correction,
                lockEvidence, List.of("틀린 곳 없어요. 다 훑고 나서 한 번에 잠그는 게 맞아요."), "락을 거는 시점이 언제인지 떠올려 보세요.")));
        samples.add(new Sample("핵심 사실 · 단답 · 답을 못 냄", new FeedbackContentRequest(
                "InnoDB에서 일반 SELECT는 행 락을 걸지 않고 무엇을 읽어 결과를 돌려주나요?", QuestionType.SHORT_ANSWER, List.of(),
                List.of("MVCC 스냅샷을 읽는다"), "MVCC 스냅샷을 읽는다.", MemoryItemKind.FACT,
                "일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다", null, null,
                List.of(new EvidenceTurn(3, "InnoDB에서 일반 SELECT도 락을 걸어?", null, null)), List.of("모르겠어요"), null)));
        samples.add(new Sample("경고 · 사례 판단 · 조건을 놓침", new FeedbackContentRequest(
                "인덱스가 없는 조건으로 SELECT ... FOR UPDATE를 실행했습니다. 잠금은 조건에 맞는 레코드에만 걸리나요? 이유와 함께 답하세요.",
                QuestionType.CASE_JUDGMENT, List.of(), List.of("인덱스 없이 쓰면 스캔한 범위 전체가 잠긴다"),
                "아니요. 인덱스가 없으면 스캔한 범위 전체가 잠깁니다.", MemoryItemKind.WARNING,
                "FOR UPDATE를 인덱스 없이 쓰면 스캔한 범위 전체가 잠긴다", null, null,
                List.of(new EvidenceTurn(2, "그럼 FOR UPDATE는 스캔 다 하고 나서 락 거는 거 아니야?", "corrected", correction)),
                List.of("조건에 맞는 것만 잠겨요."), null)));
        samples.add(new Sample("핵심 사실 · 객관식 · 오답 보기를 고름", new FeedbackContentRequest(
                "InnoDB에서 일반 SELECT의 읽기 방식으로 옳은 것은?", QuestionType.MULTIPLE_CHOICE,
                List.of("행마다 공유 락을 걸고 읽는다", "MVCC 스냅샷을 읽고 락을 걸지 않는다", "테이블 전체에 배타 락을 건다", "갭 락만 걸고 읽는다"),
                List.of("일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다"), "MVCC 스냅샷을 읽고 락을 걸지 않는다", MemoryItemKind.FACT,
                "일반 SELECT는 MVCC 스냅샷을 읽어 락을 걸지 않는다", null, null,
                List.of(new EvidenceTurn(3, "InnoDB에서 일반 SELECT도 락을 걸어?", null, null)),
                List.of("행마다 공유 락을 걸고 읽는다"), null)));
        return samples;
    }

    public static void main(String[] args) throws IOException {
        LlmFeedbackContentGenerator generator = new LlmFeedbackContentGenerator(OpenAiCli.llm());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Sample sample : samples()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sample", sample.name());
            row.put("stem", sample.request().stem());
            row.put("modelAnswer", sample.request().modelAnswer());
            row.put("previousAnswers", sample.request().previousAnswers());
            row.put("hint", attempt(() -> generator.hint(sample.request())));
            row.put("explanation", attempt(() -> generator.explanation(sample.request())));
            row.put("prerequisite", attempt(() -> generator.prerequisite(sample.request())));
            rows.add(row);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("model", OpenAiCli.model());
        report.put("samples", rows);
        OpenAiCli.print(report, List.of(args));
    }

    private static Object attempt(java.util.function.Supplier<Object> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            return Map.of("rejected", String.valueOf(e.getMessage()));
        }
    }
}
