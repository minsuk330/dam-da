package com.khack.review.collection.domain;

import java.util.List;

public record ShareExtraction(String url, int httpStatus, String title, List<ShareTurn> turns) {

    public List<String> userTurnTexts() {
        return turns.stream().filter(t -> "user".equals(t.role())).map(ShareTurn::text).toList();
    }
}
