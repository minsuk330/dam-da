package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 분야 라벨이 없는 세션(기능 도입 전 세션, 판정 도중 서버가 멈춘 세션)을 서버가 뜰 때 한 번 판정한다 (스펙 §7.10).
 * 비동기로 하나씩 돌아 서버 시작을 막지 않는다. 판정이 실패해도 미분류로 남으므로 다시 돌지 않는다.
 */
@Component
class SessionFieldBackfill {

    private static final Logger log = LoggerFactory.getLogger(SessionFieldBackfill.class);

    private final LearningSessionRepository sessions;
    private final SessionFieldService service;
    private final SessionFieldPolicy policy;

    SessionFieldBackfill(LearningSessionRepository sessions, SessionFieldService service, SessionFieldPolicy policy) {
        this.sessions = sessions;
        this.service = service;
        this.policy = policy;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void classifyUnlabeled() {
        if (!policy.autoClassify()) {
            return;
        }
        List<Long> unlabeled = sessions.findIdsWithoutField();
        if (unlabeled.isEmpty()) {
            return;
        }
        log.info("분야 라벨이 없는 학습 세션 {}개를 판정함", unlabeled.size());
        for (Long sessionId : unlabeled) {
            try {
                service.classify(sessionId);
            } catch (RuntimeException e) {
                log.warn("학습 세션 {} 분야 보충 판정 실패: {}", sessionId, e.getMessage());
            }
        }
    }
}
