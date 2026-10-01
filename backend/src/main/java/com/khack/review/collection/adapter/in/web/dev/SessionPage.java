package com.khack.review.collection.adapter.in.web.dev;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.ConfusionPoint;
import com.khack.review.collection.domain.FactKind;
import com.khack.review.collection.domain.KeyPoint;
import com.khack.review.collection.domain.ReviewUnit;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.UserTurn;
import java.util.Comparator;
import java.util.List;

final class SessionPage {

    private SessionPage() {
    }

    static String render(List<SavedSession> sessions) {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>커넥터 수신 세션</title>
                <style>
                  body { font-family: -apple-system, system-ui, sans-serif; max-width: 920px; margin: 0 auto; padding: 16px; line-height: 1.5; color: #1a1a1a; background: #fafafa; }
                  header { display: flex; justify-content: space-between; align-items: baseline; }
                  details { background: #fff; border: 1px solid #ddd; border-radius: 8px; margin: 12px 0; padding: 12px 16px; }
                  summary { cursor: pointer; font-weight: 600; }
                  .meta { color: #555; font-size: 14px; }
                  .warn { color: #b3261e; font-weight: 600; }
                  .badge { display: inline-block; font-size: 12px; padding: 1px 8px; border-radius: 10px; background: #e3ebf6; color: #1f4f8a; margin-bottom: 4px; }
                  .quote { margin: 2px 0; color: #666; font-size: 13px; }
                  .verdict { margin: 0 0 12px; font-size: 14px; font-weight: 600; color: #8a4b00; }
                  .points { margin: 0 0 8px; font-size: 14px; }
                  .unit .warning { color: #b3261e; }
                  .unit { border: 1px solid #e0e0e0; border-radius: 6px; padding: 4px 12px; margin: 8px 0; }
                  .gist { margin: 0 0 12px; padding-left: 10px; border-left: 3px solid #7a9cc6; color: #333; font-size: 14px; }
                  ol { padding-left: 24px; }
                  pre { white-space: pre-wrap; word-break: break-word; background: #f3f3f3; border-radius: 6px; padding: 8px 10px; margin: 4px 0 10px; font-size: 14px; }
                  @media (prefers-color-scheme: dark) {
                    body { background: #161616; color: #e6e6e6; }
                    details { background: #222; border-color: #3a3a3a; }
                    pre { background: #2c2c2c; }
                    .meta { color: #aaa; }
                    .warn { color: #ff8a80; }
                    .badge { display: inline-block; font-size: 12px; padding: 1px 8px; border-radius: 10px; background: #e3ebf6; color: #1f4f8a; margin-bottom: 4px; }
                  .quote { margin: 2px 0; color: #666; font-size: 13px; }
                  .verdict { margin: 0 0 12px; font-size: 14px; font-weight: 600; color: #8a4b00; }
                  .points { margin: 0 0 8px; font-size: 14px; }
                  .unit .warning { color: #b3261e; }
                  .unit { border: 1px solid #e0e0e0; border-radius: 6px; padding: 4px 12px; margin: 8px 0; }
                  .gist { color: #cfcfcf; }
                  }
                </style></head><body>
                <header><h1>커넥터 수신 세션</h1><a href="/dev/sessions.md">Markdown 다운로드</a></header>
                """);
        html.append("<p class=\"meta\">총 ").append(sessions.size()).append("건 (최신순)</p>\n");
        List<SavedSession> newestFirst = sessions.stream()
                .sorted(Comparator.comparing(SavedSession::receivedAt).reversed())
                .toList();
        for (int i = 0; i < newestFirst.size(); i++) {
            appendSession(html, newestFirst.get(i), i == 0);
        }
        return html.append("</body></html>\n").toString();
    }

    private static void appendSession(StringBuilder html, SavedSession s, boolean open) {
        html.append("<details").append(open ? " open" : "").append("><summary>")
                .append(escape(s.topicHint() == null ? "(주제 없음)" : s.topicHint()))
                .append(" · ").append(escape(s.receivedAt())).append(" · 발화 ").append(s.userTurns().size()).append("개");
        if (s.reviewUnits() != null) {
            html.append(" · 복습 단위 ").append(s.reviewUnits().size()).append("개");
        }
        html.append("</summary>\n<p class=\"meta\">id <code>").append(escape(s.id())).append("</code> · ")
                .append(escape(s.source())).append(" / ").append(escape(s.transcription())).append("</p>\n");
        if (s.warnings() != null && !s.warnings().isEmpty()) {
            html.append("<h3 class=\"warn\">서버 경고</h3>\n<ul>\n");
            s.warnings().forEach(w -> html.append("<li class=\"warn\">").append(escape(w)).append("</li>\n"));
            html.append("</ul>\n");
        }
        if (s.reviewUnits() != null && !s.reviewUnits().isEmpty()) {
            html.append("<h3>복습 단위</h3>\n");
            s.reviewUnits().forEach(u -> appendUnit(html, u, s.userTurns()));
        }
        html.append("<h3>사용자 발화</h3>\n<ol>\n");
        s.userTurns().stream()
                .sorted(Comparator.comparingInt(UserTurn::index))
                .forEach(t -> appendTurn(html, t));
        html.append("</ol>\n");
        if (s.assistantSummary() != null) {
            html.append("<h3>AI 답변 요약 (v1 요약)</h3>\n<pre>").append(escape(s.assistantSummary())).append("</pre>\n");
        }
        html.append("</details>\n");
    }

    private static void appendUnit(StringBuilder html, ReviewUnit u, List<UserTurn> turns) {
        html.append("<div class=\"unit\"><h4>").append(escape(u.title())).append("</h4><p class=\"meta\">근거 발화 ")
                .append(escape(SessionMarkdown.joined(u.evidenceTurns()))).append("</p><ul>\n");
        for (ConfusionPoint c : u.confusions()) {
            html.append("<li><b>헷갈린 지점 (").append(c.turn()).append(")</b> ").append(escape(c.userBelief()))
                    .append(" → ").append(escape(SessionMarkdown.correctionOf(turns, c.turn()))).append("</li>\n");
        }
        if (u.keyPoints() != null) {
            for (KeyPoint p : u.keyPoints()) {
                FactKind kind = p.effectiveKind();
                html.append("<li class=\"").append(kind).append("\">")
                        .append(kind == FactKind.warning ? "⚠️ " : kind == FactKind.practice ? "🛠 " : "")
                        .append(escape(p.point())).append(" <span class=\"meta\">(")
                        .append(escape(SessionMarkdown.joined(p.turns()))).append(")</span></li>\n");
            }
        }
        html.append("</ul></div>\n");
    }

    private static void appendTurn(StringBuilder html, UserTurn t) {
        html.append("<li value=\"").append(t.index()).append("\">");
        if (t.intent() != null) {
            html.append("<span class=\"badge\">").append(t.intent()).append("</span>");
        }
        if (notBlank(t.quotedText())) {
            html.append("<p class=\"quote\">인용: ").append(escape(t.quotedText())).append("</p>");
        }
        html.append("<pre>").append(escape(t.text())).append("</pre>");
        if (t.aiVerdict() != null && t.aiVerdict() != AiVerdict.not_applicable) {
            html.append("<p class=\"verdict\">AI 판정: ").append(t.aiVerdict());
            if (notBlank(t.correction())) {
                html.append(" — ").append(escape(t.correction()));
            }
            html.append("</p>");
        }
        html.append("</li>\n");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
