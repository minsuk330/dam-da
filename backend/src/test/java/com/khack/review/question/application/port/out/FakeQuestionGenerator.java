package com.khack.review.question.application.port.out;

import com.khack.review.question.domain.QuestionType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 QuestionGenerator. LLM 없이 결정적으로 문제를 만들고, 받은 요청을 기록한다.
 * 기본 동작: "[유형] 기억 항목 내용?" 문제, 객관식이면 보기 3개(정답 0번), 정답 기준은 기억 항목 내용(오류 찾기는 교정).
 *
 * <pre>{@code
 * var generator = new FakeQuestionGenerator().willRespond(new QuestionGenerationException("timeout"), question);
 * }</pre>
 */
public class FakeQuestionGenerator implements QuestionGenerator {

    private final List<QuestionRequest> calls = new CopyOnWriteArrayList<>();
    private final Deque<Object> responses = new ArrayDeque<>();

    /** 호출마다 순서대로 돌려줄 응답. {@link GeneratedQuestion} 또는 던질 {@link RuntimeException}. 다 쓰면 기본 동작. */
    public synchronized FakeQuestionGenerator willRespond(Object... responses) {
        this.responses.clear();
        this.responses.addAll(List.of(responses));
        return this;
    }

    public List<QuestionRequest> calls() {
        return List.copyOf(calls);
    }

    public void clear() {
        calls.clear();
        synchronized (this) {
            responses.clear();
        }
    }

    @Override
    public GeneratedQuestion generate(QuestionRequest request) {
        calls.add(request);
        Object next;
        synchronized (this) {
            next = responses.poll();
        }
        if (next instanceof RuntimeException failure) {
            throw failure;
        }
        if (next instanceof GeneratedQuestion question) {
            return question;
        }
        boolean multipleChoice = request.type() == QuestionType.MULTIPLE_CHOICE;
        String criterion = request.type() == QuestionType.ERROR_FINDING && request.correction() != null
                ? request.correction() : request.itemContent();
        return new GeneratedQuestion(
                "[%s] %s?%s".formatted(request.type(), request.itemContent(), request.avoidStems().isEmpty() ? "" : " (변형 " + request.avoidStems().size() + ")"),
                multipleChoice ? List.of(request.itemContent(), "오답 1", "오답 2") : List.of(),
                multipleChoice ? 0 : null,
                List.of(criterion),
                criterion,
                request.evidence().stream().map(QuestionRequest.EvidenceTurn::index).toList());
    }
}
