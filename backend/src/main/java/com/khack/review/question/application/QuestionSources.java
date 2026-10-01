package com.khack.review.question.application;

import com.khack.review.analysis.application.SessionContent;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.collection.domain.UserTurn;
import com.khack.review.question.domain.ItemKind;
import com.khack.review.question.domain.QuestionSource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** 학습 세션 내용과 대화의 사용자 발화를 문제 생성 입력으로 옮긴다. */
public final class QuestionSources {

    private QuestionSources() {
    }

    public static QuestionSource of(SessionContent content, List<UserTurn> userTurns) {
        Map<Integer, UserTurn> byIndex = userTurns.stream()
                .collect(Collectors.toMap(UserTurn::index, Function.identity(), (a, b) -> a));
        List<QuestionSource.Unit> units = content.units().stream()
                .map(unit -> new QuestionSource.Unit(unit.title(),
                        unit.items().stream().map(item -> item(item, byIndex)).toList()))
                .toList();
        List<QuestionSource.Turn> turns = userTurns.stream()
                .map(turn -> new QuestionSource.Turn(turn.index(), turn.text()))
                .toList();
        return new QuestionSource(content.sessionId(), turns, units);
    }

    private static QuestionSource.Item item(SessionContent.Item item, Map<Integer, UserTurn> turns) {
        ItemKind kind = kindOf(item.kind());
        return new QuestionSource.Item(item.id(), kind, item.content(), item.sourceTurns(),
                kind == ItemKind.CONFUSION ? correction(item, turns) : null);
    }

    /** 헷갈린 지점의 교정은 따로 저장하지 않고 출처 발화의 correction에 있다(스펙 §7.6). */
    private static @Nullable String correction(SessionContent.Item item, Map<Integer, UserTurn> turns) {
        return item.sourceTurns().stream()
                .map(turns::get)
                .filter(turn -> turn != null && turn.correction() != null && !turn.correction().isBlank())
                .map(UserTurn::correction)
                .findFirst()
                .orElse(null);
    }

    private static ItemKind kindOf(MemoryItemKind kind) {
        return switch (kind) {
            case FACT -> ItemKind.FACT;
            case WARNING -> ItemKind.WARNING;
            case PRACTICE -> ItemKind.PRACTICE;
            case CONFUSION -> ItemKind.CONFUSION;
        };
    }
}
