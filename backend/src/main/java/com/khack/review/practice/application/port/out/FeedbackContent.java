package com.khack.review.practice.application.port.out;

import java.util.List;

/**
 * 생성한 힌트 또는 개념 설명.
 *
 * @param text          사용자에게 보여 줄 본문
 * @param evidenceTurns 본문이 연결한 근거 발화 index(없으면 빈 목록)
 */
public record FeedbackContent(String text, List<Integer> evidenceTurns) {

    public FeedbackContent {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("피드백 본문이 비어 있습니다.");
        }
        evidenceTurns = List.copyOf(evidenceTurns);
    }
}
