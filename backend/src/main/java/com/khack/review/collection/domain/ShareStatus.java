package com.khack.review.collection.domain;

public enum ShareStatus {
    OK, NOT_FOUND, BLOCKED, NO_TURNS,
    /** 수집 중 타임아웃·네트워크 오류 등으로 페이지를 읽지 못했다. */
    UNREACHABLE;

    public static ShareStatus classify(ShareExtraction x) {
        return switch (x.httpStatus()) {
            case 404, 410 -> NOT_FOUND;
            case 403, 429, 503 -> BLOCKED;
            default -> x.turns().isEmpty() ? NO_TURNS : OK;
        };
    }
}
