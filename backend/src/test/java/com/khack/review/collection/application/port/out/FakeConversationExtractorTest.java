package com.khack.review.collection.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.ShareTurn;
import com.khack.review.collection.domain.UserTurn;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeConversationExtractorTest {

    @Test
    void defaultExtractionOfAShareLinkPassesTheSchemaValidator() {
        SessionInput input = new FakeConversationExtractor().extract(RawConversation.fromShareLink("표준오차", List.of(
                new ShareTurn("user", "표준오차가 뭐야?"),
                new ShareTurn("assistant", "표본평균의 퍼짐입니다."),
                new ShareTurn("user", "그럼 표준편차는?"))));

        assertThat(input.userTurns()).extracting(UserTurn::text).containsExactly("표준오차가 뭐야?", "그럼 표준편차는?");
        assertThat(SessionValidator.validate(input).errors()).isEmpty();
    }

    @Test
    void defaultExtractionOfPastedTextSplitsParagraphs() {
        SessionInput input = new FakeConversationExtractor().extract(RawConversation.fromPaste("표준오차가 뭐야?\n\n그럼 표준편차는?\n"));

        assertThat(input.userTurns()).extracting(UserTurn::text).containsExactly("표준오차가 뭐야?", "그럼 표준편차는?");
        assertThat(SessionValidator.validate(input).errors()).isEmpty();
    }
}
