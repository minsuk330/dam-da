package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSession;
import com.khack.review.analysis.domain.LearningSessionCreated;
import com.khack.review.analysis.domain.LearningSessionFieldClassified;
import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.ReviewUnit;
import com.khack.review.analysis.domain.SessionField;
import com.khack.review.analysis.domain.SessionFieldRepository;
import java.time.Clock;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 학습 세션의 분야 라벨 (스펙 §7.10). 세션 저장이 커밋된 뒤 비동기로 자동 판정하고, 사용자는 언제든 바꿀 수 있다.
 * Jev 호출은 트랜잭션 밖에서 한다. 사용자가 고른 값은 자동 판정이 덮어쓰지 않는다(규칙 19).
 * 라벨은 그래프와 목록 묶음에만 쓰며 문제 생성·판정·기억 상태에는 영향을 주지 않는다.
 */
@Service
public class SessionFieldService {

    private static final Logger log = LoggerFactory.getLogger(SessionFieldService.class);

    private final LearningSessionRepository sessions;
    private final SessionFieldRepository fields;
    private final LearningSessionQueryService query;
    private final SessionFieldClassifier classifier;
    private final FieldTaxonomy taxonomy;
    private final SessionFieldPolicy policy;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SessionFieldService(LearningSessionRepository sessions, SessionFieldRepository fields, LearningSessionQueryService query,
            SessionFieldClassifier classifier, FieldTaxonomy taxonomy, SessionFieldPolicy policy, ApplicationEventPublisher events,
            TransactionTemplate transaction, Clock clock) {
        this.sessions = sessions;
        this.fields = fields;
        this.query = query;
        this.classifier = classifier;
        this.taxonomy = taxonomy;
        this.policy = policy;
        this.events = events;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener
    public void on(LearningSessionCreated event) {
        if (policy.autoClassify()) {
            classify(event.sessionId());
        }
    }

    /** 자동 판정. 사용자가 이미 골랐으면 아무것도 하지 않는다. */
    public void classify(Long sessionId) {
        SessionFieldState state = transaction.execute(status -> stateOf(sessionId));
        if (state == null) {
            return;
        }
        SessionFieldOutcome outcome = classifier.classify(state);
        transaction.executeWithoutResult(status -> record(sessionId, outcome));
    }

    /** 현재 사용자가 세션의 분야를 고른다. 확인 전후 모두 바꿀 수 있다. */
    @Transactional
    public LearningSessionDetail choose(Long sessionId, String code) {
        if (taxonomy.subfield(code).isEmpty()) {
            throw new IllegalArgumentException("분류표에 없는 소분류입니다: " + code);
        }
        LearningSession session = query.owned(sessionId);
        fields.findById(session.getId()).ifPresentOrElse(
                field -> field.choose(code, clock.instant()),
                () -> fields.save(SessionField.chosen(session.getId(), code, clock.instant())));
        return query.detail(sessionId);
    }

    private @Nullable SessionFieldState stateOf(Long sessionId) {
        if (fields.findById(sessionId).filter(SessionField::isChosenByUser).isPresent()) {
            log.info("학습 세션 {}: 사용자가 분야를 골라 자동 판정하지 않음", sessionId);
            return null;
        }
        LearningSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션 없음: " + sessionId));
        return new SessionFieldState(session.getTopicHint(), session.getUnits().stream().map(ReviewUnit::getTitle).toList(), null);
    }

    private void record(Long sessionId, SessionFieldOutcome outcome) {
        Optional<SessionField> existing = fields.findById(sessionId);
        if (existing.filter(SessionField::isChosenByUser).isPresent()) {
            log.info("학습 세션 {}: 판정 중 사용자가 분야를 골라 결과({})를 버림", sessionId, outcome.code());
            return;
        }
        existing.ifPresentOrElse(
                field -> field.classify(outcome.code(), outcome.reason(), clock.instant()),
                () -> fields.save(SessionField.classified(sessionId, outcome.code(), outcome.reason(), clock.instant())));
        events.publishEvent(new LearningSessionFieldClassified(sessionId, outcome.code()));
        log.info("학습 세션 {} 분야 {}: {}", sessionId, outcome.code(), outcome.reason());
    }
}
