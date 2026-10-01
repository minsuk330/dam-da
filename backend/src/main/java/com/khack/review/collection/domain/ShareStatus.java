package com.khack.review.collection.domain;

public enum ShareStatus {
    OK, NOT_FOUND, BLOCKED, NO_TURNS;

    public static ShareStatus classify(ShareExtraction x) {
        return switch (x.httpStatus()) {
            case 404, 410 -> NOT_FOUND;
            case 403, 429, 503 -> BLOCKED;
            default -> x.turns().isEmpty() ? NO_TURNS : OK;
        };
    }
}
