package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습 세션 조회. 다른 컨텍스트는 이 서비스로 세션 내용을 읽는다. */
@Service
public class LearningSessionQueryService {

    private final LearningSessionRepository sessions;

    public LearningSessionQueryService(LearningSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** 없으면 {@link IllegalArgumentException}. */
    @Transactional(readOnly = true)
    public SessionContent content(Long sessionId) {
        return sessions.findById(sessionId)
                .map(SessionContent::of)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션 없음: " + sessionId));
    }
}
