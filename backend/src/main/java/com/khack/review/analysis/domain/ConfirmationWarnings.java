package com.khack.review.analysis.domain;

import com.khack.review.collection.domain.AiVerdict;
import com.khack.review.collection.domain.Intent;
import com.khack.review.collection.domain.UserTurn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 확인 화면의 재검증 결과. 사용자가 고칠 때마다 현재 발화와 기억 항목으로 다시 계산한다(도메인 스토리 S1-8).
 * 커넥터 검증({@code SessionValidator})의 경고 중 사용자가 확인 화면에서 바로잡을 수 있는 것만 사용자 말투로 보여준다.
 * 경고는 확인을 막지 않는다.
 */
public final class ConfirmationWarnings {

    private static final Set<AiVerdict> CORRECTED = Set.of(AiVerdict.partial, AiVerdict.corrected);

    private ConfirmationWarnings() {
    }

    public static List<String> of(LearningSession session, List<UserTurn> turns) {
        Set<Integer> sources = new HashSet<>();
        Set<Integer> confusions = new HashSet<>();
        for (MemoryItem item : session.items()) {
            if (item.isExcluded()) {
                continue;
            }
            sources.addAll(item.getSourceTurns());
            if (item.getKind() == MemoryItemKind.CONFUSION) {
                confusions.addAll(item.getSourceTurns());
            }
        }
        List<String> warnings = new ArrayList<>();
        for (UserTurn turn : turns) {
            boolean corrected = CORRECTED.contains(turn.effectiveVerdict());
            if (corrected && (turn.correction() == null || turn.correction().isBlank())) {
                warnings.add("%d번 발화: AI가 바로잡았다고 표시됐지만 교정 내용이 비어 있습니다.".formatted(turn.index()));
            }
            if (turn.intent() == Intent.meta) {
                continue;
            }
            if (!sources.contains(turn.index())) {
                warnings.add("%d번 발화: 어느 기억 항목에도 연결되지 않았습니다.".formatted(turn.index()));
            } else if (corrected && !confusions.contains(turn.index())) {
                warnings.add("%d번 발화: AI가 바로잡은 발화인데 헷갈린 지점 항목이 없습니다.".formatted(turn.index()));
            }
        }
        return List.copyOf(warnings);
    }
}
