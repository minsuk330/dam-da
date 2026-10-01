package com.khack.review.practice.application;

public class PracticeNotFoundException extends RuntimeException {

    public PracticeNotFoundException(String what, Long id) {
        super("%s %d을(를) 찾을 수 없습니다.".formatted(what, id));
    }
}
