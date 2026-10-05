package com.khack.review.collection.adapter.out.playwright;

import com.khack.review.collection.application.port.out.ShareLinkFetcher;
import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareLink;
import com.khack.review.collection.domain.ShareSource;
import com.khack.review.collection.domain.ShareTurn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 공유 페이지를 headless Chromium으로 열어 발화를 읽는다. 출처마다 메시지 DOM이 달라 {@link Layout}으로 읽는 방법을 고른다
 * (스펙 §7.5). Claude·Codex 페이지는 Cloudflare가 headless 표시가 붙은 UA({@code HeadlessChrome})를 막아 그 표시를 떼고 연다.
 */
@Component
public class ShareExtractor implements ShareLinkFetcher {

    /** 출처별 메시지 셀렉터와, 메시지 요소 하나를 발화로 바꾸는 방법. 메시지 요소는 문서 순서대로 읽는다. */
    record Layout(String messageSelector, boolean plainUserAgent, Function<Locator, ShareTurn> toTurn) {

        static Layout of(ShareSource source) {
            return switch (source) {
                case chatgpt -> new Layout("[data-message-author-role]", false, el -> new ShareTurn(
                        Objects.requireNonNullElse(el.getAttribute("data-message-author-role"), "unknown"), el.innerText()));
                case claude -> new Layout("[data-testid=\"user-message\"], .font-claude-response", true, el -> new ShareTurn(
                        "user-message".equals(el.getAttribute("data-testid")) ? "user" : "assistant", el.innerText()));
                case codex -> new Layout("article[id^=\"message-\"]", true, el -> {
                    Locator bubble = el.locator("[data-user-message-bubble]");
                    return bubble.count() > 0
                            ? new ShareTurn("user", bubble.first().innerText())
                            : new ShareTurn("assistant", el.innerText());
                });
            };
        }
    }

    @Override
    public ShareExtraction fetch(ShareLink link) {
        return extract(link, false, null);
    }

    public static ShareExtraction extract(ShareLink link, boolean headed, String dumpName) {
        Layout layout = Layout.of(link.source());
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!headed))) {
            Browser.NewPageOptions options = new Browser.NewPageOptions().setLocale("ko-KR");
            if (layout.plainUserAgent()) {
                options.setUserAgent(plainUserAgent(browser));
            }
            Page page = browser.newPage(options);
            Response response = page.navigate(link.url(), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(45_000));
            try {
                page.waitForSelector(layout.messageSelector(), new Page.WaitForSelectorOptions().setTimeout(20_000));
            } catch (TimeoutError e) {
                // no message elements rendered; classified as NO_TURNS or BLOCKED below and by the caller
            }

            int previous = -1;
            for (int i = 0; i < 20; i++) {
                int count = page.locator(layout.messageSelector()).count();
                if (count == previous) {
                    break;
                }
                previous = count;
                page.keyboard().press("End");
                page.mouse().wheel(0, 20_000);
                page.waitForTimeout(700);
            }

            List<ShareTurn> turns = page.locator(layout.messageSelector()).all().stream().map(layout.toTurn()).toList();

            if (dumpName != null) {
                Path dir = Path.of("data/results");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(dumpName + ".html"), page.content());
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(dumpName + ".png")).setFullPage(true));
            }

            int status = response == null ? 0 : response.status();
            if (turns.isEmpty() && leftSharePage(link, page.url())) {
                // 봇 확인(챌린지)이나 로그인 페이지로 넘어갔다. 응답이 200이어도 막힌 것으로 본다.
                status = 403;
            }
            return new ShareExtraction(link.url(), status, page.title(), turns);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 이 브라우저의 기본 UA에서 headless 표시만 뗀다. 운영체제 표기는 실제 실행 환경 그대로 둔다. */
    private static String plainUserAgent(Browser browser) {
        Page probe = browser.newPage();
        try {
            return ((String) probe.evaluate("navigator.userAgent")).replace("HeadlessChrome", "Chrome");
        } finally {
            probe.close();
        }
    }

    static boolean leftSharePage(ShareLink link, String landedUrl) {
        return !URI.create(link.url()).getPath().equals(URI.create(landedUrl).getPath());
    }
}
