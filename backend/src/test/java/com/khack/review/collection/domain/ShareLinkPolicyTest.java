package com.khack.review.collection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShareLinkPolicyTest {

    @Test
    void allowsChatGptShareLinksAndDropsQueryAndFragment() {
        assertThat(ShareLinkPolicy.requireAllowed(" https://chatgpt.com/share/abc-123?utm=x#top "))
                .isEqualTo(new ShareLink("https://chatgpt.com/share/abc-123", ShareSource.chatgpt));
        assertThat(ShareLinkPolicy.requireAllowed("https://CHAT.OPENAI.COM/share/abc"))
                .isEqualTo(new ShareLink("https://chat.openai.com/share/abc", ShareSource.chatgpt));
    }

    @Test
    void allowsClaudeAndCodexShareLinks() {
        assertThat(ShareLinkPolicy.requireAllowed("https://claude.ai/share/b3bc5b41-f390-44b8-a1c0-c7b5f8afa493"))
                .isEqualTo(new ShareLink("https://claude.ai/share/b3bc5b41-f390-44b8-a1c0-c7b5f8afa493", ShareSource.claude));
        assertThat(ShareLinkPolicy.requireAllowed("https://chatgpt.com/s/cx_6ac32a8d562081918971867890db3ea6?x=1"))
                .isEqualTo(new ShareLink("https://chatgpt.com/s/cx_6ac32a8d562081918971867890db3ea6", ShareSource.codex));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://chatgpt.com/share/abc",
            "https://chatgpt.com.evil.com/share/abc",
            "https://evil.com/share/abc",
            "https://chatgpt.com:8443/share/abc",
            "https://user@chatgpt.com/share/abc",
            "https://chatgpt.com/c/abc",
            "https://chatgpt.com/share/",
            "https://chatgpt.com/s/abc",
            "https://chatgpt.com/s/cx_",
            "https://chat.openai.com/s/cx_abc",
            "https://claude.ai/chat/abc",
            "https://claude.ai/share/",
            "https://claude.ai.evil.com/share/abc",
            "http://claude.ai/share/abc",
            "https://169.254.169.254/share/abc",
            "file:///etc/passwd",
            "not a url",
            ""})
    void rejectsEverythingElse(String url) {
        assertThatThrownBy(() -> ShareLinkPolicy.requireAllowed(url)).isInstanceOf(IllegalArgumentException.class);
    }
}
