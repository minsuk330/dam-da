package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.SessionFieldRepository;
import com.khack.review.common.domain.AccountDeleted;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 탈퇴한 사용자의 학습 세션(복습 단위·기억 항목 포함)과 분야 라벨을 지운다(#148). */
@Component
class SessionAccountDeletion {

    private final LearningSessionRepository sessions;
    private final SessionFieldRepository fields;

    SessionAccountDeletion(LearningSessionRepository sessions, SessionFieldRepository fields) {
        this.sessions = sessions;
        this.fields = fields;
    }

    @EventListener
    void on(AccountDeleted event) {
        List<Long> sessionIds = sessions.findIdsByUserId(event.userId());
        if (!sessionIds.isEmpty()) {
            fields.deleteBySessionIdIn(sessionIds);
        }
        sessions.deleteByUserId(event.userId());
    }
}
