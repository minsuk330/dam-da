package com.khack.review.question.application.port.out;

import com.khack.review.question.domain.QuestionType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 QuestionGenerator. LLM 없이 결정적으로 문제를 만들고, 받은 요청을 기록한다.
 * 기본 동작: 대상마다 "[유형] 기억 항목 내용?" 문제, 객관식이면 보기 4개(정답 0번), 정답 기준은 기억 항목 내용(오류 찾기는 교정).
 *
 * <pre>{@code
 * var generator = new FakeQuestionGenerator().willRespond(new QuestionGenerationException("timeout"));
 * }</pre>
 */
public class FakeQuestionGenerator implements QuestionGenerator {

    private final List<UnitQuestionRequest> calls = new CopyOnWriteArrayList<>();
    private final Deque<Object> responses = new ArrayDeque<>();

    /** 호출마다 순서대로 쓸 응답. {@link UnitQuestionResult} 또는 던질 {@link RuntimeException}. 다 쓰면 기본 동작. */
    public synchronized FakeQuestionGenerator willRespond(Object... responses) {
        this.responses.clear();
        this.responses.addAll(List.of(responses));
        return this;
    }

    public List<UnitQuestionRequest> calls() {
        return List.copyOf(calls);
    }

    /** 모든 대상 요청을 순서대로 펼친 목록. */
    public List<UnitQuestionRequest.Target> targets() {
        return calls.stream().flatMap(c -> c.targets().stream()).toList();
    }

    public void clear() {
        calls.clear();
        synchronized (this) {
            responses.clear();
        }
    }

    @Override
    public UnitQuestionResult generate(UnitQuestionRequest request) {
        calls.add(request);
        Object next;
        synchronized (this) {
            next = responses.poll();
        }
        if (next instanceof RuntimeException failure) {
            throw failure;
        }
        if (next instanceof UnitQuestionResult result) {
            return result;
        }
        return new UnitQuestionResult(request.targets().stream().map(FakeQuestionGenerator::question).toList());
    }

    public static UnitQuestionResult.TargetResult question(UnitQuestionRequest.Target target) {
        boolean multipleChoice = target.type() == QuestionType.MULTIPLE_CHOICE;
        String criterion = target.type() == QuestionType.ERROR_FINDING && target.correction() != null
                ? target.correction() : target.itemContent();
        return UnitQuestionResult.TargetResult.made(target.targetId(), new GeneratedQuestion(
                "[%s] %s?%s".formatted(target.type(), target.itemContent(),
                        target.avoidStems().isEmpty() ? "" : " (변형 " + target.avoidStems().size() + ")"),
                multipleChoice ? List.of(target.itemContent(), "오답 1", "오답 2", "오답 3") : List.of(),
                multipleChoice ? 0 : null,
                List.of(criterion),
                criterion,
                "떠올릴 방향",
                "개념 설명",
                target.sourceTurns()));
    }
}
