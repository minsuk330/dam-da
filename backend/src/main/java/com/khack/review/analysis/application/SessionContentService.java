package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 컨텍스트(문제 생성 등)가 학습 세션의 학습 대상 내용을 읽는 곳. 엔티티 대신 {@link SessionContent}를 돌려준다. */
@Service
public class SessionContentService {

    private final LearningSessionRepository sessions;

    public SessionContentService(LearningSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public SessionContent content(Long sessionId) {
        return sessions.findById(sessionId)
                .map(SessionContent::of)
                .orElseThrow(() -> new LearningSessionNotFoundException(sessionId));
    }
}
