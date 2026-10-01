package com.khack.review.collection.adapter.out.playwright;

import com.khack.review.collection.application.port.out.ShareLinkFetcher;
import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareTurn;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ShareExtractor implements ShareLinkFetcher {

    static final String MESSAGE_SELECTOR = "[data-message-author-role]";

    @Override
    public ShareExtraction fetch(String url) {
        return extract(url, false, null);
    }

    public static ShareExtraction extract(String url, boolean headed, String dumpName) {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!headed))) {
            Page page = browser.newPage(new Browser.NewPageOptions().setLocale("ko-KR"));
            Response response = page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(45_000));
            try {
                page.waitForSelector(MESSAGE_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(20_000));
            } catch (TimeoutError e) {
                // no message elements rendered; classified as NO_TURNS or BLOCKED by the caller
            }

            int previous = -1;
            for (int i = 0; i < 20; i++) {
                int count = page.locator(MESSAGE_SELECTOR).count();
                if (count == previous) {
                    break;
                }
                previous = count;
                page.keyboard().press("End");
                page.mouse().wheel(0, 20_000);
                page.waitForTimeout(700);
            }

            List<ShareTurn> turns = page.locator(MESSAGE_SELECTOR).all().stream()
                    .map(el -> new ShareTurn(
                            Objects.requireNonNullElse(el.getAttribute("data-message-author-role"), "unknown"),
                            el.innerText()))
                    .toList();

            if (dumpName != null) {
                Path dir = Path.of("data/results");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(dumpName + ".html"), page.content());
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(dumpName + ".png")).setFullPage(true));
            }

            return new ShareExtraction(url, response == null ? 0 : response.status(), page.title(), turns);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
