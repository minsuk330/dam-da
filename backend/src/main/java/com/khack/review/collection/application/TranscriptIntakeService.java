package com.khack.review.collection.application;

import com.khack.review.collection.application.port.out.ConversationExtractionException;
import com.khack.review.collection.application.port.out.ConversationExtractor;
import com.khack.review.collection.application.port.out.ShareLinkFetcher;
import com.khack.review.collection.domain.ConversationSubmitted;
import com.khack.review.collection.domain.LearningConversation;
import com.khack.review.collection.domain.LearningConversationRepository;
import com.khack.review.collection.domain.RawConversation;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.collection.domain.SessionValidator;
import com.khack.review.collection.domain.ShareExtraction;
import com.khack.review.collection.domain.ShareLink;
import com.khack.review.collection.domain.ShareLinkPolicy;
import com.khack.review.collection.domain.ShareStatus;
import com.khack.review.collection.domain.ShareTurn;
import com.khack.review.collection.domain.TranscriptAlignment;
import com.khack.review.collection.domain.ValidationResult;
import com.khack.review.common.application.CurrentUser;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공유 링크·붙여넣기 입력 (스펙 §7.5). 원문을 받아 추출 포트로 커넥터 스키마 v5를 만들고, 원문과 맞춘 뒤
 * 커넥터와 같은 검증을 거쳐 저장한다. 저장하면 {@code ConversationSubmitted}를 발행해 학습 세션이 만들어진다.
 * 링크 수집과 추출은 오래 걸리므로 트랜잭션 밖에서 하고, 저장만 트랜잭션으로 묶는다.
 */
@Service
public class TranscriptIntakeService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptIntakeService.class);

    /** 붙여넣기 원문 상한. 저장 컬럼(원문 JSON)보다 작게 둔다. */
    static final int MAX_PASTED_CHARS = 200_000;

    private final ShareLinkFetcher fetcher;
    private final ObjectProvider<ConversationExtractor> extractor;
    private final LearningConversationRepository conversations;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    public TranscriptIntakeService(ShareLinkFetcher fetcher, ObjectProvider<ConversationExtractor> extractor,
            LearningConversationRepository conversations, CurrentUser currentUser, Clock clock,
            ApplicationEventPublisher events, TransactionTemplate transaction) {
        this.fetcher = fetcher;
        this.extractor = extractor;
        this.conversations = conversations;
        this.currentUser = currentUser;
        this.clock = clock;
        this.events = events;
        this.transaction = transaction;
    }

    public SavedSession fromShareLink(String url) {
        ShareLink allowed = ShareLinkPolicy.requireAllowed(url);
        ShareExtraction fetched;
        try {
            fetched = fetcher.fetch(allowed);
        } catch (RuntimeException e) {
            log.warn("share link fetch failed: {}", allowed.url(), e);
            throw new ShareLinkUnavailableException(ShareStatus.UNREACHABLE);
        }
        ShareStatus status = ShareStatus.classify(fetched);
        if (status != ShareStatus.OK || fetched.userTurnTexts().isEmpty()) {
            throw new ShareLinkUnavailableException(status == ShareStatus.OK ? ShareStatus.NO_TURNS : status);
        }
        int size = fetched.turns().stream().map(ShareTurn::text).mapToInt(t -> t == null ? 0 : t.length()).sum();
        if (size > MAX_PASTED_CHARS * 4) {
            throw new IllegalArgumentException("대화가 너무 깁니다. 학습한 부분만 붙여넣기로 입력하세요.");
        }
        return intake(RawConversation.fromShareLink(allowed.source(), fetched.title(), fetched.turns()));
    }

    public SavedSession fromPaste(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("붙여넣을 대화를 입력하세요.");
        }
        if (text.length() > MAX_PASTED_CHARS) {
            throw new IllegalArgumentException("대화가 너무 깁니다. %d자 이하로 학습한 부분만 붙여넣으세요.".formatted(MAX_PASTED_CHARS));
        }
        return intake(RawConversation.fromPaste(text));
    }

    private SavedSession intake(RawConversation raw) {
        ConversationExtractor available = extractor.getIfAvailable();
        if (available == null) {
            throw new ConversationExtractionException("대화 추출기가 아직 구성되지 않았습니다.");
        }
        SessionInput extracted = available.extract(raw);
        TranscriptAlignment.Result aligned = TranscriptAlignment.align(raw, extracted);
        ValidationResult result = SessionValidator.validate(aligned.input());
        if (!result.errors().isEmpty()) {
            throw new SessionRejectedException(result.errors());
        }
        List<String> warnings = new ArrayList<>(aligned.warnings());
        warnings.addAll(result.warnings());
        return transaction.execute(tx -> {
            LearningConversation saved = conversations.save(LearningConversation.fromTranscript(
                    currentUser.id(), raw, aligned.input(), warnings, clock.instant()));
            events.publishEvent(new ConversationSubmitted(saved.getId(), saved.getUserId(), aligned.input(), saved.getReceivedAt()));
            return saved.toSavedSession();
        });
    }
}
