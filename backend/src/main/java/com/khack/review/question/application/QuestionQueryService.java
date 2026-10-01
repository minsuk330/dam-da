package com.khack.review.question.application;

import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 컨텍스트가 문제 1개를 읽는 곳(풀이 제시·채점). */
@Service
public class QuestionQueryService {

    private final QuestionRepository questions;

    public QuestionQueryService(QuestionRepository questions) {
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public Question question(Long questionId) {
        return questions.findById(questionId).orElseThrow(() -> new IllegalArgumentException("문제 없음: " + questionId));
    }
}
