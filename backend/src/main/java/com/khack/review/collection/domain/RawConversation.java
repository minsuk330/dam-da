package com.khack.review.collection.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 공유 링크나 붙여넣기로 받은 원문 대화. 둘 중 하나만 가진다.
 * <ul>
 *   <li>공유 링크: 발화자(`user`·`assistant`)가 구분된 {@code turns}</li>
 *   <li>붙여넣기: 발화자 구분이 없는 {@code pastedText}</li>
 * </ul>
 */
public record RawConversation(InputPath inputPath, @Nullable String title, List<ShareTurn> turns, @Nullable String pastedText) {

    public static RawConversation fromShareLink(@Nullable String title, List<ShareTurn> turns) {
        return new RawConversation(InputPath.share_link, title, List.copyOf(turns), null);
    }

    public static RawConversation fromPaste(String pastedText) {
        return new RawConversation(InputPath.paste, null, List.of(), pastedText);
    }

    /** 공유 링크 원문의 사용자 발화. 붙여넣기는 발화자를 알 수 없어 비어 있다. */
    public List<String> userTurnTexts() {
        return turns.stream().filter(turn -> "user".equals(turn.role())).map(ShareTurn::text).toList();
    }
}
