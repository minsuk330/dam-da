package com.khack.review.practice.application.port.out;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 FeedbackContentGenerator. LLM 없이 결정적인 본문을 만들고 받은 요청을 기록한다.
 * 기본 동작: 힌트는 "힌트: 문제 문장", 설명은 "설명: 항목 내용"(믿었던 내용이 있으면 함께), 선행 개념은 "선행: 항목 내용".
 *
 * <pre>{@code
 * var generator = new FakeFeedbackContentGenerator().willFail(new FeedbackGenerationException("timeout"));
 * }</pre>
 */
public class FakeFeedbackContentGenerator implements FeedbackContentGenerator {

    private final List<FeedbackContentRequest> hints = new CopyOnWriteArrayList<>();
    private final List<FeedbackContentRequest> explanations = new CopyOnWriteArrayList<>();
    private final List<FeedbackContentRequest> prerequisites = new CopyOnWriteArrayList<>();
    private final Deque<RuntimeException> failures = new ArrayDeque<>();

    /** 다음 호출(종류 무관)들이 순서대로 던질 예외. 다 쓰면 기본 동작. */
    public synchronized FakeFeedbackContentGenerator willFail(RuntimeException... failures) {
        this.failures.clear();
        this.failures.addAll(List.of(failures));
        return this;
    }

    public List<FeedbackContentRequest> hintRequests() {
        return List.copyOf(hints);
    }

    public List<FeedbackContentRequest> explanationRequests() {
        return List.copyOf(explanations);
    }

    public List<FeedbackContentRequest> prerequisiteRequests() {
        return List.copyOf(prerequisites);
    }

    public synchronized void clear() {
        hints.clear();
        explanations.clear();
        prerequisites.clear();
        failures.clear();
    }

    @Override
    public FeedbackContent hint(FeedbackContentRequest request) {
        hints.add(request);
        failIfRequested();
        return new FeedbackContent("힌트: " + request.stem(), turns(request));
    }

    @Override
    public FeedbackContent explanation(FeedbackContentRequest request) {
        explanations.add(request);
        failIfRequested();
        String belief = request.userBelief() == null ? "" : " (믿었던 내용: " + request.userBelief() + ")";
        return new FeedbackContent("설명: " + request.itemContent() + belief, turns(request));
    }

    @Override
    public PrerequisiteSuggestion prerequisite(FeedbackContentRequest request) {
        prerequisites.add(request);
        failIfRequested();
        return new PrerequisiteSuggestion("선행: " + request.itemContent(), "반복해서 틀린 항목");
    }

    private synchronized void failIfRequested() {
        RuntimeException failure = failures.poll();
        if (failure != null) {
            throw failure;
        }
    }

    private static List<Integer> turns(FeedbackContentRequest request) {
        return request.evidence().stream().map(FeedbackContentRequest.EvidenceTurn::index).toList();
    }
}
