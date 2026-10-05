package com.khack.review.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.khack.review.collection.adapter.out.playwright.ShareExtractor;
import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareLink;
import com.khack.review.collection.domain.ShareLinkPolicy;
import com.khack.review.collection.domain.ShareSource;
import com.khack.review.collection.domain.ShareStatus;
import com.khack.review.collection.domain.ShareTurn;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 실제 공유 페이지를 headless Chromium으로 열어 발화를 읽는다. 공유 링크는 개인 대화라 커밋하지 않고
 * {@code backend/.env}의 {@code LIVE_SHARE_URL_<출처>}에서 읽는다. 없으면 건너뛴다.
 */
@Tag("live")
class LiveShareLinkIT {

    @ParameterizedTest
    @CsvSource({"LIVE_SHARE_URL_CHATGPT", "LIVE_SHARE_URL_CLAUDE", "LIVE_SHARE_URL_CODEX"})
    void readsUserAndAssistantTurnsInOrder(String key) {
        String url = System.getenv(key);
        assumeTrue(url != null && !url.isBlank(), key + " is not set");
        ShareLink link = ShareLinkPolicy.requireAllowed(url);
        assertThat(link.source()).isEqualTo(ShareSource.valueOf(key.substring("LIVE_SHARE_URL_".length()).toLowerCase()));

        ShareExtraction extraction = ShareExtractor.extract(link, false, null);

        assertThat(ShareStatus.classify(extraction)).isEqualTo(ShareStatus.OK);
        assertThat(extraction.turns()).extracting(ShareTurn::role).containsOnly("user", "assistant");
        assertThat(extraction.turns().getFirst().role()).isEqualTo("user");
        assertThat(extraction.userTurnTexts()).isNotEmpty().allSatisfy(text -> assertThat(text).isNotBlank());
        assertThat(extraction.turns().size()).isGreaterThan(extraction.userTurnTexts().size());
    }
}
