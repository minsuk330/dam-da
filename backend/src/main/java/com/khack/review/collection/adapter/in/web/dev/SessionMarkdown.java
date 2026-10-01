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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SessionMarkdown {

    private static final Pattern BACKTICK_RUN = Pattern.compile("`+");

    private SessionMarkdown() {
    }

    public static String render(List<SavedSession> sessions) {
        StringBuilder md = new StringBuilder("# 커넥터 수신 세션\n\n");
        md.append("총 ").append(sessions.size()).append("건 (최신순)\n");
        sessions.stream()
                .sorted(Comparator.comparing(SavedSession::receivedAt).reversed())
                .forEach(s -> appendSession(md, s));
        return md.toString();
    }

    private static void appendSession(StringBuilder md, SavedSession s) {
        md.append("\n---\n\n## ").append(blankTo(s.topicHint(), "(주제 없음)")).append("\n\n");
        md.append("- id: `").append(s.id()).append("`\n");
        md.append("- 수신 시각: ").append(s.receivedAt()).append("\n");
        md.append("- 출처: ").append(s.source()).append(" / ").append(s.transcription()).append("\n");
        md.append("- 사용자 발화 ").append(s.userTurns().size()).append("개");
        if (s.reviewUnits() != null) {
            md.append(", 복습 단위 ").append(s.reviewUnits().size()).append("개");
        }
        md.append("\n");
        if (s.warnings() != null && !s.warnings().isEmpty()) {
            md.append("\n### ⚠️ 서버 경고\n\n");
            s.warnings().forEach(w -> md.append("- ").append(w).append("\n"));
        }
        if (s.reviewUnits() != null && !s.reviewUnits().isEmpty()) {
            md.append("\n### 복습 단위\n");
            s.reviewUnits().forEach(u -> appendUnit(md, u, s.userTurns()));
        }
        md.append("\n### 사용자 발화\n");
        s.userTurns().stream()
                .sorted(Comparator.comparingInt(UserTurn::index))
                .forEach(t -> appendTurn(md, t));
        if (s.assistantSummary() != null) {
            md.append("\n### AI 답변 요약 (v1 요약)\n\n").append(s.assistantSummary()).append("\n");
        }
    }

    private static void appendUnit(StringBuilder md, ReviewUnit u, List<UserTurn> turns) {
        md.append("\n#### ").append(u.title()).append("\n\n");
        md.append("- 근거 발화: ").append(joined(u.evidenceTurns())).append("\n");
        for (ConfusionPoint c : u.confusions()) {
            md.append("- 헷갈린 지점 (").append(c.turn()).append("): ")
                    .append(c.userBelief()).append(" → ").append(correctionOf(turns, c.turn())).append("\n");
        }
        if (u.keyPoints() != null) {
            for (KeyPoint p : u.keyPoints()) {
                md.append("- ").append(marker(p.effectiveKind())).append(oneLine(p.point()))
                        .append(" (").append(joined(p.turns())).append(")\n");
            }
        }
    }

    private static void appendTurn(StringBuilder md, UserTurn t) {
        md.append("\n**").append(t.index()).append("**");
        if (t.intent() != null) {
            md.append(" `").append(t.intent()).append("`");
        }
        md.append("\n\n");
        if (notBlank(t.quotedText())) {
            md.append("> 인용: ").append(oneLine(t.quotedText())).append("\n\n");
        }
        md.append(fenced(t.text())).append("\n");
        if (t.aiVerdict() != null && notBlank(t.correction())) {
            md.append("\n> AI 판정: ").append(t.aiVerdict()).append(" — ").append(oneLine(t.correction())).append("\n");
        } else if (t.aiVerdict() != null && t.aiVerdict() != AiVerdict.not_applicable) {
            md.append("\n> AI 판정: ").append(t.aiVerdict()).append("\n");
        }
    }

    static String joined(List<Integer> turns) {
        return turns == null ? "" : turns.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    static String correctionOf(List<UserTurn> turns, int index) {
        return turns.stream().filter(t -> t.index() == index).map(UserTurn::correction)
                .filter(SessionMarkdown::notBlank).findFirst().orElse("(교정 내용 없음)");
    }

    private static String marker(FactKind kind) {
        return switch (kind) {
            case warning -> "⚠️ ";
            case practice -> "🛠 ";
            case fact -> "";
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankTo(String s, String fallback) {
        return notBlank(s) ? s : fallback;
    }

    private static String oneLine(String s) {
        return s.replace("\n", " ");
    }

    private static String fenced(String text) {
        int longest = 0;
        Matcher m = BACKTICK_RUN.matcher(text);
        while (m.find()) {
            longest = Math.max(longest, m.group().length());
        }
        String fence = "`".repeat(Math.max(3, longest + 1));
        return fence + "text\n" + text + "\n" + fence;
    }
}
