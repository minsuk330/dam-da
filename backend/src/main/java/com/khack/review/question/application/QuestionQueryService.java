package com.khack.review.question.application;

import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionStatus;
import java.util.List;
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

    /** 기억 항목의 승인된 문제(오래된 순). 매일 학습이 항목마다 낼 문제를 고를 때 쓴다. */
    @Transactional(readOnly = true)
    public List<Question> approvedOf(Long memoryItemId) {
        return questions.findByMemoryItemIdAndStatusOrderByIdAsc(memoryItemId, QuestionStatus.APPROVED);
    }
}
