package com.khack.review.practice.application;

import com.khack.review.practice.domain.QuestionHeldAsAmbiguous;
import com.khack.review.practice.domain.QuestionRecheck;
import com.khack.review.practice.domain.QuestionRecheckRepository;
import com.khack.review.practice.domain.RecheckResult;
import com.khack.review.question.application.QuestionGenerationService;
import com.khack.review.question.application.QuestionQueryService;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 모호해서 보류된 문제를 문제마다 한 번 재검사한다(스펙 §6.4.5 보류 처리). 답변 판정 직후 비동기로 하고({@link QuestionHeldAsAmbiguous}),
 * 피드백의 변형 문제 요청도 여기로 와서 같은 결과를 쓴다. 재검사(LLM·Jev)는 트랜잭션 밖에서 하고 결과만 {@link QuestionRecheck}에 남긴다.
 *
 * <p>진행 중인 재검사는 문제별 {@link CompletableFuture}로 묶어, 늦게 온 쪽은 새로 부르지 않고 그 결과를 기다린다(단일 서버 전제).
 * 끝난 결과는 기록에서 읽는다. 실패한 기록은 피드백 요청에서만 한 번 더 시도한다.
 */
@Service
public class QuestionRecheckService {

    private static final Logger log = LoggerFactory.getLogger(QuestionRecheckService.class);
    private static final Duration WAIT = Duration.ofMinutes(2);

    private final QuestionRecheckRepository rechecks;
    private final QuestionQueryService questions;
    private final QuestionGenerationService generation;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final ConcurrentMap<Long, CompletableFuture<Optional<Long>>> inFlight = new ConcurrentHashMap<>();

    public QuestionRecheckService(QuestionRecheckRepository rechecks, QuestionQueryService questions,
            QuestionGenerationService generation, TransactionTemplate transaction, Clock clock) {
        this.rechecks = rechecks;
        this.questions = questions;
        this.generation = generation;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** 답변 판정 직후. 제출 응답을 막지 않도록 비동기로 한다. 실패해도 다시 시도하지 않는다. */
    @Async
    @EventListener
    public void on(QuestionHeldAsAmbiguous event) {
        recheck(event.questionId(), event.userId(), event.presentationId(), false);
    }

    /**
     * 문제를 재검사하고 쓸 수 있는 문제 ID를 돌려준다(통과면 그 문제, 폐기면 변형 문제). 이미 끝났으면 기록을 쓰고, 진행 중이면 기다린다.
     *
     * @param retryFailed 지난 재검사가 실패했으면 다시 시도할지
     */
    public Optional<Long> recheck(Long questionId, Long userId, Long presentationId, boolean retryFailed) {
        CompletableFuture<Optional<Long>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<Long>> running = inFlight.putIfAbsent(questionId, mine);
        if (running != null) {
            return await(questionId, running);
        }
        try {
            Optional<Long> usable = once(questionId, userId, presentationId, retryFailed);
            mine.complete(usable);
            return usable;
        } catch (RuntimeException e) {
            mine.complete(Optional.empty());
            throw e;
        } finally {
            inFlight.remove(questionId, mine);
        }
    }

    private Optional<Long> once(Long questionId, Long userId, Long presentationId, boolean retryFailed) {
        Optional<QuestionRecheck> done = rechecks.findByQuestionId(questionId);
        if (done.isPresent() && (done.get().getResult() != RecheckResult.FAILED || !retryFailed)) {
            return done.get().usableQuestionId();
        }
        if (questions.question(questionId).getStatus() != QuestionStatus.APPROVED) {
            log.info("문제 {}: 승인 상태가 아니라 재검사하지 않음", questionId);
            return Optional.empty();
        }
        RecheckResult result;
        Long variant = null;
        String failure = null;
        try {
            Optional<Question> usable = generation.recheck(questionId);
            if (usable.map(Question::getId).filter(questionId::equals).isPresent()) {
                result = RecheckResult.PASSED;
            } else {
                result = RecheckResult.RETIRED;
                variant = usable.map(Question::getId).orElse(null);
            }
        } catch (RuntimeException e) {
            log.warn("문제 {} 재검사 실패: {}", questionId, e.getMessage());
            result = RecheckResult.FAILED;
            failure = e.getMessage();
        }
        log.info("문제 {} 재검사(제시 {}): {}{}", questionId, presentationId, result, variant == null ? "" : ", 변형 " + variant);
        RecheckResult decided = result;
        Long variantId = variant;
        String note = failure;
        return transaction.execute(tx -> {
            QuestionRecheck recheck = rechecks.findByQuestionId(questionId)
                    .orElseGet(() -> new QuestionRecheck(questionId, userId, presentationId));
            recheck.record(decided, variantId, note, clock.instant());
            return rechecks.save(recheck).usableQuestionId();
        });
    }

    private static Optional<Long> await(Long questionId, CompletableFuture<Optional<Long>> running) {
        try {
            return running.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException e) {
            log.warn("문제 {}: 진행 중인 재검사를 기다리지 못함: {}", questionId, e.toString());
            return Optional.empty();
        }
    }
}
