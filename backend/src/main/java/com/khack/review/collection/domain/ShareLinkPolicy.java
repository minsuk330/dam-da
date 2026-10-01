package com.khack.review.collection.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * 서버 브라우저로 열어도 되는 공유 링크인지 검사한다. 사용자 입력 URL을 그대로 열면 서버가 내부망·메타데이터 주소를
 * 대신 요청하게 되므로(SSRF), 해커톤 대상인 ChatGPT 공유 링크만 허용한다(스펙 §7.5).
 */
public final class ShareLinkPolicy {

    private static final Set<String> HOSTS = Set.of("chatgpt.com", "chat.openai.com");

    private ShareLinkPolicy() {
    }

    /** 허용되면 정규화한 URL을, 아니면 {@link IllegalArgumentException}. */
    public static String requireAllowed(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("공유 링크를 입력하세요.");
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("공유 링크 형식이 아닙니다.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean allowed = "https".equalsIgnoreCase(uri.getScheme())
                && HOSTS.contains(host)
                && uri.getPort() == -1
                && uri.getUserInfo() == null
                && path.startsWith("/share/")
                && path.length() > "/share/".length();
        if (!allowed) {
            throw new IllegalArgumentException("ChatGPT 공유 링크(https://chatgpt.com/share/...)만 지원합니다.");
        }
        return "https://" + host + path;
    }
}
