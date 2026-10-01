package com.khack.review.common.adapter.in.web.connector;

import org.springframework.web.util.HtmlUtils;

/** 커넥터 로그인·연결 승인 화면의 공통 틀. Claude가 연 브라우저 창에 뜨는 서버 렌더링 페이지다. */
final class ConnectorPages {

    private ConnectorPages() {
    }

    static String escape(String text) {
        return HtmlUtils.htmlEscape(text == null ? "" : text);
    }

    static String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                  :root { --bg: #f6f7fb; --card: #ffffff; --text: #1d2433; --muted: #5d6679; --primary: #3a5bd9; --line: #e3e6ee; }
                  * { box-sizing: border-box; }
                  body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 24px 16px;
                         background: var(--bg); color: var(--text); font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif; }
                  main { width: 100%%; max-width: 380px; background: var(--card); border-radius: 20px; padding: 28px 24px;
                         box-shadow: 0 8px 30px rgba(29, 36, 51, .08); }
                  h1 { font-size: 20px; margin: 0 0 8px; }
                  p { color: var(--muted); line-height: 1.5; margin: 0 0 16px; font-size: 15px; }
                  ul { margin: 0 0 20px; padding: 14px 16px 14px 32px; border: 1px solid var(--line); border-radius: 12px; font-size: 15px; line-height: 1.6; }
                  .button { display: block; width: 100%%; padding: 14px; margin-top: 10px; border-radius: 12px; border: 1px solid var(--line);
                            background: var(--card); color: var(--text); font-size: 16px; font-weight: 600; text-align: center; text-decoration: none; cursor: pointer; }
                  .primary { background: var(--primary); border-color: var(--primary); color: #fff; }
                  .kakao { background: #fee500; border-color: #fee500; color: #191919; }
                  .error { color: #c62828; }
                  form { margin: 0; }
                </style>
                </head>
                <body><main>%s</main></body>
                </html>
                """.formatted(escape(title), body);
    }
}
