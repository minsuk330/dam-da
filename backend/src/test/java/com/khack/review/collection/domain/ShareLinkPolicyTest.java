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
                .isEqualTo("https://chatgpt.com/share/abc-123");
        assertThat(ShareLinkPolicy.requireAllowed("https://CHAT.OPENAI.COM/share/abc"))
                .isEqualTo("https://chat.openai.com/share/abc");
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
            "https://169.254.169.254/share/abc",
            "file:///etc/passwd",
            "not a url",
            ""})
    void rejectsEverythingElse(String url) {
        assertThatThrownBy(() -> ShareLinkPolicy.requireAllowed(url)).isInstanceOf(IllegalArgumentException.class);
    }
}
