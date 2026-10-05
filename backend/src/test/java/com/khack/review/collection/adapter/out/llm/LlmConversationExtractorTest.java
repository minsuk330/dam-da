package com.khack.review.collection.adapter.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.khack.review.collection.application.port.out.ConversationExtractionException;
import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.ShareSource;
import com.khack.review.collection.domain.ShareTurn;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.common.application.port.out.FakeLlmPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmConversationExtractorTest {

    static final RawConversation SHARED = RawConversation.fromShareLink(ShareSource.chatgpt, "ETag", List.of(
            new ShareTurn("user", "ETag가 뭐야?"),
            new ShareTurn("assistant", "버전 식별자입니다."),
            new ShareTurn("user", "요청마다 파일을 비교하는 거지?"),
            new ShareTurn("assistant", "아닙니다. 문자열만 비교합니다."),
            new ShareTurn("user", "복습에 넣어줘")));

    final FakeLlmPort llm = new FakeLlmPort();
    final LlmConversationExtractor extractor = new LlmConversationExtractor(llm);

    static UserTurn classified(int index, Intent intent, AiVerdict verdict, String correction) {
        return new UserTurn(index, null, " ", intent, verdict, correction);
    }

    static ReviewUnit unit(int... turns) {
        return new ReviewUnit("ETag", List.of(new KeyPoint("ETag는 버전 식별자다", java.util.Arrays.stream(turns).boxed().toList(), null)),
                List.of());
    }

    static SessionInput allTurns() {
        return new SessionInput(List.of(
                classified(3, Intent.meta, null, null),
                classified(1, Intent.info_request, null, null),
                classified(2, Intent.understanding_check, AiVerdict.corrected, "문자열만 비교한다")),
                List.of(new ReviewUnit("ETag", List.of(new KeyPoint("ETag는 버전 식별자다", List.of(1, 2), null)),
                        List.of(new ConfusionPoint(2, "요청마다 파일을 비교한다")))),
                " ");
    }

    @Test
    void shareLinkTurnsTakeOriginalTextAndOrderWhileTheModelOnlyClassifies() {
        llm.willReturn(allTurns());

        SessionInput input = extractor.extract(SHARED);

        assertThat(input.userTurns()).extracting(UserTurn::index).containsExactly(1, 2, 3);
        assertThat(input.userTurns()).extracting(UserTurn::text)
                .containsExactly("ETag가 뭐야?", "요청마다 파일을 비교하는 거지?", "복습에 넣어줘");
        assertThat(input.userTurns()).extracting(UserTurn::intent)
                .containsExactly(Intent.info_request, Intent.understanding_check, Intent.meta);
        assertThat(input.userTurns().get(1).correction()).isEqualTo("문자열만 비교한다");
        assertThat(input.userTurns()).allSatisfy(turn -> assertThat(turn.quotedText()).isNull());
        assertThat(input.topicHint()).isNull();
        assertThat(input.reviewUnits().get(0).confusions()).containsExactly(new ConfusionPoint(2, "요청마다 파일을 비교한다"));

        FakeLlmPort.Call call = llm.calls().get(0);
        assertThat(call.systemPrompt()).isEqualTo(ExtractionPrompt.SYSTEM);
        assertThat(call.userPrompt())
                .contains("\"mode\":\"share_link\"", "\"userTurnIndex\":2", "\"afterUserTurn\":2", "아닙니다. 문자열만 비교합니다.");
    }

    @Test
    void missingTurnIsRequestedAgainWithTheProblem() {
        SessionInput missing = new SessionInput(List.of(classified(1, Intent.info_request, null, null),
                classified(2, Intent.understanding_check, null, null)), List.of(unit(1)), null);
        llm.willReturn(missing, allTurns());

        SessionInput input = extractor.extract(SHARED);

        assertThat(input.userTurns()).hasSize(3);
        assertThat(llm.calls()).hasSize(2);
        assertThat(llm.calls().get(1).userPrompt()).contains("이전 출력의 오류", "userTurnIndex 3번");
    }

    @Test
    void pasteKeepsTheModelsTurnsAndSendsTheTextAsData() {
        RawConversation pasted = RawConversation.fromPaste("ETag가 뭐야?\n\n버전 식별자입니다.");
        llm.willReturn(new SessionInput(List.of(new UserTurn(1, "ETag가 뭐야?", null, Intent.info_request, null, "")),
                List.of(unit(1)), "HTTP 캐시"));

        SessionInput input = extractor.extract(pasted);

        assertThat(input.userTurns()).containsExactly(new UserTurn(1, "ETag가 뭐야?", null, Intent.info_request, null, null));
        assertThat(input.reviewUnits().get(0).confusionPoints()).isNull();
        assertThat(input.topicHint()).isEqualTo("HTTP 캐시");
        assertThat(llm.calls().get(0).userPrompt()).contains("\"mode\":\"paste\"", "버전 식별자입니다.");
    }

    @Test
    void structuralErrorIsRetriedOnceThenLeftForServerValidation() {
        SessionInput dangling = new SessionInput(allTurns().userTurns(), List.of(unit(9)), null);
        llm.willReturn(dangling, dangling);

        SessionInput input = extractor.extract(SHARED);

        assertThat(llm.calls()).hasSize(2);
        assertThat(llm.calls().get(1).userPrompt()).contains("9번 발화가 userTurns에 없습니다");
        assertThat(input.reviewUnits().get(0).keyPoints().get(0).turns()).containsExactly(9);
    }

    @Test
    void failsWhenTheModelNeverClassifiesEveryTurn() {
        SessionInput missing = new SessionInput(List.of(classified(1, Intent.info_request, null, null)), List.of(unit(1)), null);
        llm.willReturn(missing, missing);

        assertThatThrownBy(() -> extractor.extract(SHARED))
                .isInstanceOf(ConversationExtractionException.class)
                .hasMessageContaining("userTurnIndex 2번");
    }

    @Test
    void llmFailureIsRetriedThenReportedAsExtractionFailure() {
        llm.willReturn(new IllegalStateException("과부하"), new IllegalStateException("과부하"));

        assertThatThrownBy(() -> extractor.extract(SHARED))
                .isInstanceOf(ConversationExtractionException.class)
                .hasMessageContaining("과부하");
        assertThat(llm.calls()).hasSize(2);
    }

    @Test
    void tooLongConversationIsRejectedWithoutCallingTheModel() {
        RawConversation huge = RawConversation.fromPaste("가".repeat(LlmConversationExtractor.MAX_INPUT_CHARS));

        assertThatThrownBy(() -> extractor.extract(huge)).isInstanceOf(ConversationExtractionException.class);
        assertThat(llm.calls()).isEmpty();
    }
}
