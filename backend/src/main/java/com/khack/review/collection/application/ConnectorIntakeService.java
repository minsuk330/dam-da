package com.khack.review.collection.application;

import com.khack.review.collection.domain.ConversationSubmitted;
import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.ValidationResult;
import com.khack.review.common.application.CurrentUser;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커넥터 입력을 검증해 저장한다. 구조 오류는 거부하고, 의미상 이상은 경고로 함께 저장한다.
 * 저장하면 {@link ConversationSubmitted}를 같은 트랜잭션에서 발행한다.
 */
@Service
public class ConnectorIntakeService {

    private final LearningConversationRepository conversations;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public ConnectorIntakeService(LearningConversationRepository conversations, CurrentUser currentUser, Clock clock,
            ApplicationEventPublisher events) {
        this.conversations = conversations;
        this.currentUser = currentUser;
        this.clock = clock;
        this.events = events;
    }

    @Transactional
    public SavedSession intake(SessionInput input) {
        ValidationResult result = SessionValidator.validate(input);
        if (!result.errors().isEmpty()) {
            throw new SessionRejectedException(result.errors());
        }
        LearningConversation conversation = LearningConversation.fromConnector(
                currentUser.id(), input, result.warnings(), clock.instant());
        LearningConversation saved = conversations.save(conversation);
        events.publishEvent(new ConversationSubmitted(saved.getId(), saved.getUserId(), input, saved.getReceivedAt()));
        return saved.toSavedSession();
    }
}
