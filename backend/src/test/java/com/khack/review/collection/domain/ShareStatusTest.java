package com.khack.review.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShareStatusTest {

    private static ShareExtraction page(int status, List<ShareTurn> turns) {
        return new ShareExtraction("https://chatgpt.com/share/x", status, "t", turns);
    }

    @Test
    void notFoundFor404InsteadOfEmptySuccess() {
        assertThat(ShareStatus.classify(page(404, List.of()))).isEqualTo(ShareStatus.NOT_FOUND);
    }

    @Test
    void blockedFor403And429() {
        assertThat(ShareStatus.classify(page(403, List.of()))).isEqualTo(ShareStatus.BLOCKED);
        assertThat(ShareStatus.classify(page(429, List.of()))).isEqualTo(ShareStatus.BLOCKED);
    }

    @Test
    void noTurnsFor200PageWithoutMessages() {
        assertThat(ShareStatus.classify(page(200, List.of()))).isEqualTo(ShareStatus.NO_TURNS);
    }

    @Test
    void okWhenMessagesExist() {
        assertThat(ShareStatus.classify(page(200, List.of(new ShareTurn("user", "hi"))))).isEqualTo(ShareStatus.OK);
    }

    @Test
    void userTurnTextsKeepsOnlyUserTurnsInOrder() {
        ShareExtraction x = page(200, List.of(
                new ShareTurn("user", "Q1"), new ShareTurn("assistant", "A1"), new ShareTurn("user", "Q2")));
        assertThat(x.userTurnTexts()).containsExactly("Q1", "Q2");
    }
}
