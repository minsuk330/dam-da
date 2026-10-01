package com.khack.review.collection.application;

import java.util.List;

/**
 * 서버가 고칠 수 없는 구조 오류로 커넥터 입력을 거부했다.
 */
public class SessionRejectedException extends RuntimeException {

    private final List<String> errors;

    public SessionRejectedException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
