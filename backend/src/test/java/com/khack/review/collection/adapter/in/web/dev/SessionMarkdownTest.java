package com.khack.review.collection.adapter.in.web.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.UserTurn;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionMarkdownTest {

    private static final UserTurn CHECK = new UserTurn(1, "스캔하고 락 거는 거 아니야?", "클러스터 인덱스를",
            Intent.understanding_check, AiVerdict.corrected, "스캔 후가 아니라 읽는 순간");
    private static final UserTurn INFO = new UserTurn(2, "trx_id가 뭔데", null, Intent.info_request, null, null);

    private static SavedSession v5(String id, String receivedAt, List<UserTurn> turns, List<ReviewUnit> units, List<String> warnings) {
        return new SavedSession(id, receivedAt, "connector", "model_transcribed", turns, units, "InnoDB", warnings, null, null);
    }

    private static ReviewUnit unit() {
        return new ReviewUnit("잠금 순서",
                List.of(new KeyPoint("레코드마다 즉시 잠근다", List.of(1), null),
                        new KeyPoint("RC에서도 갭 락이 걸린다", List.of(1, 2), FactKind.warning),
                        new KeyPoint("EXPLAIN type ALL 확인", List.of(2), FactKind.practice)),
                List.of(new ConfusionPoint(1, "스캔 후 잠근다")));
    }

    @Test
    void rendersNewestSessionFirst() {
        String md = SessionMarkdown.render(List.of(
                v5("old-id", "2026-09-27T10:00:00Z", List.of(CHECK), List.of(unit()), List.of()),
                v5("new-id", "2026-09-27T11:00:00Z", List.of(CHECK), List.of(unit()), List.of())));
        assertThat(md.indexOf("new-id")).isLessThan(md.indexOf("old-id"));
    }

    @Test
    void rendersTurnWithIntentQuoteAndVerdict() {
        String md = SessionMarkdown.render(List.of(v5("id", "2026-09-27T10:00:00Z", List.of(CHECK, INFO), List.of(unit()), List.of())));
        assertThat(md).contains("`understanding_check`", "인용: 클러스터 인덱스를", "AI 판정: corrected — 스캔 후가 아니라 읽는 순간", "`info_request`");
    }

    @Test
    void rendersReviewUnitWithComputedEvidenceConfusionAndKeyPoints() {
        String md = SessionMarkdown.render(List.of(v5("id", "2026-09-27T10:00:00Z", List.of(CHECK, INFO), List.of(unit()), List.of("reviewUnits: 경고"))));
        assertThat(md).contains("#### 잠금 순서", "근거 발화: 1, 2", "헷갈린 지점 (1): 스캔 후 잠근다 → 스캔 후가 아니라 읽는 순간",
                "- 레코드마다 즉시 잠근다 (1)", "- ⚠️ RC에서도 갭 락이 걸린다 (1, 2)", "- 🛠 EXPLAIN type ALL 확인 (2)", "서버 경고", "reviewUnits: 경고");
    }

    @Test
    void keepsTurnTextVerbatimInsideFencesLongerThanAnyInnerFence() {
        String text = "이 코드 왜 이래?\n```js\nconsole.log(1)\n```";
        UserTurn turn = new UserTurn(1, text, null, Intent.info_request, null, null);
        String md = SessionMarkdown.render(List.of(v5("id", "2026-09-27T10:00:00Z", List.of(turn), List.of(), List.of())));
        assertThat(md).contains("````text\n" + text + "\n````");
    }

    @Test
    void rendersLegacySessionSummary() {
        SavedSession legacy = new SavedSession("old", "2026-09-27T10:00:00Z", "connector", "model_transcribed",
                List.of(new UserTurn(1, "질문", null, null, null, null)), null, null, null, "## 옛 요약", 1);
        assertThat(SessionMarkdown.render(List.of(legacy))).contains("질문", "v1 요약", "## 옛 요약");
    }
}
