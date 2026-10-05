package com.khack.review.question.application;

import com.khack.review.common.domain.AccountDeleted;
import com.khack.review.question.domain.QuestionRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴한 사용자의 문제를 지운다(#148). */
@Component
class QuestionAccountDeletion {

    private final QuestionRepository questions;

    QuestionAccountDeletion(QuestionRepository questions) {
        this.questions = questions;
    }

    @EventListener
    void on(AccountDeleted event) {
        questions.deleteByUserId(event.userId());
    }
}
