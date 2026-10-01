package com.khack.review.collection.application.port.out;

import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.UserTurn;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 테스트용 ConversationExtractor. LLM 없이 결정적으로 추출하고, 받은 요청을 기록한다.
 * 기본 동작: 공유 링크는 사용자 발화를, 붙여넣기는 빈 줄로 나눈 문단을 순서대로 사용자 발화(`info_request`)로 보고,
 * 모든 발화를 근거로 하는 복습 단위 1개를 만든다.
 *
 * <pre>{@code
 * var extractor = new FakeConversationExtractor().willReturn(input);
 * }</pre>
 */
public class FakeConversationExtractor implements ConversationExtractor {

    private final List<RawConversation> calls = new ArrayList<>();
    private SessionInput next;
    private RuntimeException failure;

    public FakeConversationExtractor willReturn(SessionInput input) {
        this.next = input;
        this.failure = null;
        return this;
    }

    public FakeConversationExtractor willFail(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public List<RawConversation> calls() {
        return List.copyOf(calls);
    }

    @Override
    public SessionInput extract(RawConversation conversation) {
        calls.add(conversation);
        if (failure != null) {
            throw failure;
        }
        if (next != null) {
            return next;
        }
        List<String> texts = conversation.pastedText() == null
                ? conversation.userTurnTexts()
                : Arrays.stream(conversation.pastedText().split("\\n\\s*\\n")).map(String::strip).filter(t -> !t.isEmpty()).toList();
        List<UserTurn> turns = IntStream.range(0, texts.size())
                .mapToObj(i -> new UserTurn(i + 1, texts.get(i), null, Intent.info_request, null, null))
                .toList();
        List<Integer> all = turns.stream().map(UserTurn::index).toList();
        return new SessionInput(turns,
                List.of(new ReviewUnit(conversation.title() == null ? "대화 주제" : conversation.title(),
                        List.of(new KeyPoint("대화에서 설명한 핵심", all, null)), null)),
                conversation.title());
    }
}
