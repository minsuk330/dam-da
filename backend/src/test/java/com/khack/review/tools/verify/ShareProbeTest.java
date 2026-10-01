package com.khack.review.tools.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShareProbeTest {

    private static final Map<String, String> HTML = Map.of("Content-Type", "text/html; charset=utf-8");

    @Test
    void pickNeedlesUsesFirst20CharactersOfFirstAndLastTurn() {
        assertThat(ShareProbe.pickNeedles(List.of("TCP랑 UDP 차이를 쉽게 설명해줘 제발", "중간", "마지막 질문")))
                .containsExactly("TCP랑 UDP 차이를 쉽게 설명해줘", "마지막 질문");
    }

    @Test
    void findsPlainTextNeedlesAndMarksStaticViable() {
        ProbeReport r = ShareProbe.analyze(200, HTML,
                "<div data-message-author-role=\"user\">TCP랑 UDP</div>", List.of("TCP랑 UDP"));
        assertThat(r.staticViable()).isTrue();
        assertThat(r.hasRoleAttr()).isTrue();
        assertThat(r.needlesMissing()).isEmpty();
    }

    @Test
    void findsNeedlesJsonEscapedInsideEmbeddedScriptData() {
        String body = "<script>window.__data=\"TCP\\ub791 \\\"UDP\\\"\"</script>";
        ProbeReport r = ShareProbe.analyze(200, HTML, body, List.of("TCP랑 \"UDP\""));
        assertThat(r.needlesFound()).containsExactly("TCP랑 \"UDP\"");
        assertThat(r.staticViable()).isTrue();
    }

    @Test
    void reportsCloudflareChallengeAsNotViable() {
        ProbeReport r = ShareProbe.analyze(403,
                Map.of("Content-Type", "text/html", "CF-Ray", "abc", "Server", "cloudflare"),
                "Just a moment...", List.of("TCP"));
        assertThat(r.cloudflare()).isTrue();
        assertThat(r.staticViable()).isFalse();
        assertThat(r.needlesMissing()).containsExactly("TCP");
    }
}
