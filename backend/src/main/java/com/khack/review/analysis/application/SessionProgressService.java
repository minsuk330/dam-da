package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.LearningSessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 컨텍스트가 학습 세션의 진행 상태를 넘기는 곳. */
@Service
public class SessionProgressService {

    private final LearningSessionRepository sessions;

    public SessionProgressService(LearningSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** 첫 학습 문제 생성이 끝났다. 확인 완료 상태일 때만 문제 준비로 넘기고, 이미 넘어갔으면 그대로 둔다. */
    @Transactional
    public void markQuestionsReady(Long sessionId) {
        LearningSession session = sessions.findById(sessionId).orElseThrow(() -> new LearningSessionNotFoundException(sessionId));
        if (session.getStatus() == LearningSessionStatus.CONFIRMED) {
            session.moveTo(LearningSessionStatus.QUESTIONS_READY);
        }
    }

    /** 첫 학습 풀이를 시작했다. 문제 준비 상태일 때만 학습 중으로 넘기고, 이미 넘어갔으면 그대로 둔다. */
    @Transactional
    public void markStudyStarted(Long sessionId) {
        LearningSession session = sessions.findById(sessionId).orElseThrow(() -> new LearningSessionNotFoundException(sessionId));
        if (session.getStatus() == LearningSessionStatus.QUESTIONS_READY) {
            session.moveTo(LearningSessionStatus.IN_PROGRESS);
        }
    }
}
