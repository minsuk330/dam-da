package com.khack.review.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TranscriptAlignmentTest {

    static final RawConversation SHARE = RawConversation.fromShareLink(ShareSource.chatgpt, "통계", List.of(
            new ShareTurn("user", "표준오차가 뭐얘요?"),
            new ShareTurn("assistant", "표본평균의 퍼짐입니다."),
            new ShareTurn("user", "그럼 표준편차는?")));

    static SessionInput extracted(String... texts) {
        List<UserTurn> turns = new java.util.ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            turns.add(new UserTurn(i + 1, texts[i], null, Intent.info_request, null, null));
        }
        return new SessionInput(turns, List.of(new ReviewUnit("통계", List.of(new KeyPoint("핵심", List.of(1), null)), null)), null);
    }

    @Test
    void shareLinkTextsAreReplacedWithTheOriginalWhenCountsMatch() {
        TranscriptAlignment.Result result = TranscriptAlignment.align(SHARE, extracted("표준오차가 뭐예요?", "그럼 표준편차는?"));

        assertThat(result.input().userTurns()).extracting(UserTurn::text).containsExactly("표준오차가 뭐얘요?", "그럼 표준편차는?");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void shareLinkCountMismatchKeepsTheExtractionAndWarns() {
        TranscriptAlignment.Result result = TranscriptAlignment.align(SHARE, extracted("표준오차가 뭐예요?"));

        assertThat(result.input().userTurns()).extracting(UserTurn::text).containsExactly("표준오차가 뭐예요?");
        assertThat(result.warnings()).singleElement().asString().contains("2개", "1개");
    }

    @Test
    void pastedTextWarnsOnlyForTurnsNotFoundInTheOriginal() {
        RawConversation paste = RawConversation.fromPaste("나: 표준오차가   뭐야?\nAI: 표본평균의 퍼짐\n나: 그럼 표준편차는?");

        TranscriptAlignment.Result result = TranscriptAlignment.align(paste, extracted("표준오차가 뭐야?", "표준편차는 무엇인가요?"));

        assertThat(result.warnings()).singleElement().asString().startsWith("userTurns[1].text");
    }
}
