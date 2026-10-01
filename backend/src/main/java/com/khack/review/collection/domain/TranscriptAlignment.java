package com.khack.review.collection.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 추출 결과의 사용자 발화를 원문과 맞춘다. 공유 링크와 붙여넣기 입력은 원문을 보장하는 경로다(`verbatim`, 규칙 14).
 * <ul>
 *   <li>공유 링크: 추출된 발화 수가 원문 사용자 발화 수와 같으면 text를 원문으로 덮어쓴다. 다르면 경고만 남긴다.</li>
 *   <li>붙여넣기: 발화자를 알 수 없으므로, 추출된 text가 원문에 그대로 있는지 확인해 없으면 경고를 남긴다.</li>
 * </ul>
 */
public final class TranscriptAlignment {

    private TranscriptAlignment() {
    }

    public record Result(SessionInput input, List<String> warnings) {
    }

    public static Result align(RawConversation raw, SessionInput extracted) {
        List<String> warnings = new ArrayList<>();
        List<UserTurn> turns = extracted.userTurns() == null ? List.of() : extracted.userTurns();
        if (raw.pastedText() != null) {
            String source = normalize(raw.pastedText());
            for (int i = 0; i < turns.size(); i++) {
                String text = turns.get(i).text();
                if (text != null && !source.contains(normalize(text))) {
                    warnings.add("userTurns[%d].text: 붙여넣은 원문에 같은 문장이 없습니다. 추출 과정에서 바뀌었을 수 있습니다.".formatted(i));
                }
            }
            return new Result(extracted, warnings);
        }
        List<String> originals = raw.userTurnTexts();
        if (originals.size() != turns.size()) {
            warnings.add("userTurns: 원문 사용자 발화는 %d개인데 %d개가 추출됐습니다. 확인 단계에서 발화를 확인하세요."
                    .formatted(originals.size(), turns.size()));
            return new Result(extracted, warnings);
        }
        List<UserTurn> aligned = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            UserTurn t = turns.get(i);
            aligned.add(new UserTurn(t.index(), originals.get(i), t.quotedText(), t.intent(), t.aiVerdict(), t.correction()));
        }
        return new Result(new SessionInput(aligned, extracted.reviewUnits(), extracted.topicHint()), warnings);
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
