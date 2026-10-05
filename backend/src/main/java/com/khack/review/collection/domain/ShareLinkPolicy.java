package com.khack.review.collection.domain;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 서버 브라우저로 열어도 되는 공유 링크인지 검사한다. 사용자 입력 URL을 그대로 열면 서버가 내부망·메타데이터 주소를
 * 대신 요청하게 되므로(SSRF), 아래 공유 페이지만 허용한다(스펙 §7.5).
 * <ul>
 *   <li>ChatGPT: {@code https://chatgpt.com/share/<id>}, {@code https://chat.openai.com/share/<id>}</li>
 *   <li>Codex: {@code https://chatgpt.com/s/cx_<id>}</li>
 *   <li>Claude: {@code https://claude.ai/share/<id>}</li>
 * </ul>
 */
public final class ShareLinkPolicy {

    private ShareLinkPolicy() {
    }

    /** 허용되면 정규화한 링크와 출처를, 아니면 {@link IllegalArgumentException}. */
    public static ShareLink requireAllowed(String url) {
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
        ShareSource source = "https".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1 && uri.getUserInfo() == null
                ? sourceOf(host, path)
                : null;
        if (source == null) {
            throw new IllegalArgumentException("ChatGPT·Claude·Codex 공유 링크만 지원합니다.");
        }
        return new ShareLink("https://" + host + path, source);
    }

    private static ShareSource sourceOf(String host, String path) {
        return switch (host) {
            case "chatgpt.com" -> hasId(path, "/share/") ? ShareSource.chatgpt
                    : hasId(path, "/s/cx_") ? ShareSource.codex : null;
            case "chat.openai.com" -> hasId(path, "/share/") ? ShareSource.chatgpt : null;
            case "claude.ai" -> hasId(path, "/share/") ? ShareSource.claude : null;
            default -> null;
        };
    }

    private static boolean hasId(String path, String prefix) {
        return path.startsWith(prefix) && path.length() > prefix.length();
    }
}
