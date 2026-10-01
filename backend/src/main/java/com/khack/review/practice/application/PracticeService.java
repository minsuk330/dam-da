package com.khack.review.practice.application;

import com.khack.review.analysis.application.LearningSessionDetail;
import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.application.SessionProgressService;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.MemoryStateService;
import com.khack.review.memory.application.ReviewRecordService;
import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.memory.domain.HoldReason;
import com.khack.review.memory.domain.RatingDecision;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.ReviewContext;
import com.khack.review.memory.domain.SelfAssessment;
import com.khack.review.practice.domain.AidExposure;
import com.khack.review.practice.domain.AidExposureRepository;
import com.khack.review.practice.domain.AidType;
import com.khack.review.practice.domain.AnswerFailure;
import com.khack.review.practice.domain.AnswerJudgment;
import com.khack.review.practice.domain.AnswerJudgmentRepository;
import com.khack.review.practice.domain.AttemptRules;
import com.khack.review.practice.domain.JudgedBy;
import com.khack.review.practice.domain.JudgmentStatus;
import com.khack.review.practice.domain.PracticeAttempt;
import com.khack.review.practice.domain.PracticeAttemptRepository;
import com.khack.review.practice.domain.DailyPracticeCompleted;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.practice.domain.PracticeQueueEntry;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.practice.domain.QuestionPresentation;
import com.khack.review.practice.domain.QuestionPresentationRepository;
import com.khack.review.question.application.QuestionQueryService;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import io.github.openspacedrepetition.Rating;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 풀이 진행과 답변 제출 (스펙 §6.4.5, §9.4). 문제를 제시하고 답과 시도 정보를 기록한다.
 * 답은 제출하자마자 판정한다(객관식은 코드, 그 밖에는 Jev). 등급 변환, FSRS 갱신, 힌트·설명 내용은 여기서 하지 않는다.
 * 정답은 응답에 넣지 않는다.
 */
@Service
public class PracticeService {

    private final PracticeSessionRepository practices;
    private final QuestionPresentationRepository presentations;
    private final AidExposureRepository aids;
    private final PracticeAttemptRepository attempts;
    private final SessionProgressService progress;
    private final FirstStudyQueryService firstStudy;
    private final QuestionQueryService questions;
    private final MemoryStateService memory;
    private final ReviewRecordService reviews;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final AnswerJudgmentRepository judgments;
    private final LearningSessionQueryService sessions;
    private final AnswerJudge judge;
    private final AnswerJudgePolicy judgePolicy;
    private final TransactionTemplate transaction;
    private final Duration multipleChoiceGuessTime;
    private final ApplicationEventPublisher events;

    public PracticeService(PracticeSessionRepository practices, QuestionPresentationRepository presentations,
            AidExposureRepository aids, PracticeAttemptRepository attempts, SessionProgressService progress, FirstStudyQueryService firstStudy, QuestionQueryService questions,
            MemoryStateService memory, ReviewRecordService reviews, CurrentUser currentUser, Clock clock,
            AnswerJudgmentRepository judgments, LearningSessionQueryService sessions, AnswerJudge judge, AnswerJudgePolicy judgePolicy,
            TransactionTemplate transaction, @Value("${review.practice.mc-guess-threshold:3s}") Duration multipleChoiceGuessTime,
            ApplicationEventPublisher events) {
        this.practices = practices;
        this.presentations = presentations;
        this.aids = aids;
        this.attempts = attempts;
        this.progress = progress;
        this.firstStudy = firstStudy;
        this.questions = questions;
        this.memory = memory;
        this.reviews = reviews;
        this.currentUser = currentUser;
        this.clock = clock;
        this.judgments = judgments;
        this.sessions = sessions;
        this.judge = judge;
        this.judgePolicy = judgePolicy;
        this.transaction = transaction;
        this.multipleChoiceGuessTime = multipleChoiceGuessTime;
        this.events = events;
    }

    public record PracticeView(Long practiceId, PracticeKind kind, @Nullable Long learningSessionId, int total,
            boolean completed) {
    }

    public record PresentationView(Long presentationId, int position, int total, Long questionId, QuestionType type,
            String stem, List<String> choices, boolean sameDayRecheck) {
    }

    /** 다음 문제. 다 풀었으면 {@code done}이 참이고 문제가 없다. */
    public record Next(boolean done, @Nullable PresentationView presentation) {
    }

    public record AidView(Long aidId, AidType type, Instant exposedAt) {
    }

    /**
     * 답변. 객관식은 {@code choiceIndex}(0부터), 그 밖에는 {@code answer}. 시간은 클라이언트가 잰 밀리초이며 비워도 된다.
     */
    public record Submission(@Nullable String answer, @Nullable Integer choiceIndex, @Nullable SelfAssessment selfAssessment,
            @Nullable Long responseTimeMs, @Nullable Long firstInputMs) {
    }

    /** 제출 결과. {@code correct}는 객관식 코드 채점 결과이고 서술형이면 null이다(판정은 Jev). */
    /**
     * 제출 결과. {@code correct}는 객관식 코드 채점 결과이고 서술형이면 null이다(판정은 {@code judgment}).
     * {@code rating}·{@code holdReason}은 등급 변환 결과다. 평가 대상이 아니거나 판정하지 못했으면 둘 다 null이다.
     */
    public record AttemptView(Long attemptId, AttemptKind kind, boolean evaluated, @Nullable Boolean correct,
            long responseTimeMs, JudgmentView judgment, @Nullable Rating rating, @Nullable HoldReason holdReason) {
    }

    /**
     * 판정 결과. {@code judged}가 거짓이면 판정하지 못해 기억 상태를 바꾸지 않는다. {@code reason}은 `not_met`의 대표 이유,
     * {@code misconceptionRecurred}는 대화에서 믿었던 틀린 내용을 다시 주장했는지다.
     */
    public record JudgmentView(boolean judged, @Nullable AnswerVerdict verdict, @Nullable AnswerFailure reason,
            boolean misconceptionRecurred) {

        static JudgmentView of(AnswerJudgment judgment) {
            return new JudgmentView(judgment.getStatus() == JudgmentStatus.JUDGED, judgment.getVerdict(),
                    judgment.getPrimaryFailure(), judgment.isMisconceptionRecurred());
        }
    }

    /** 첫 학습 풀이를 시작한다. 이미 시작했으면 그 풀이를 이어서 연다. */
    @Transactional
    public PracticeView startFirstStudy(Long learningSessionId) {
        Optional<PracticeSession> existing = practices.findFirstByLearningSessionIdAndKindOrderByIdAsc(learningSessionId,
                PracticeKind.FIRST_STUDY).filter(p -> p.getUserId().equals(currentUser.id()));
        if (existing.isPresent()) {
            return view(existing.get());
        }
        FirstStudyQueryService.FirstStudy plan = firstStudy.firstStudy(learningSessionId);
        if (plan.sessionStatus() != LearningSessionStatus.QUESTIONS_READY && plan.sessionStatus() != LearningSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("학습 세션 %d은(는) %s 상태라 첫 학습을 시작할 수 없습니다. 문제가 준비된 뒤에 시작합니다."
                    .formatted(learningSessionId, plan.sessionStatus()));
        }
        List<Long> questionIds = plan.questions().stream().map(FirstStudyQueryService.QuestionView::questionId).toList();
        if (questionIds.isEmpty()) {
            throw new IllegalStateException("학습 세션 %d에는 낼 수 있는 문제가 없습니다.".formatted(learningSessionId));
        }
        PracticeSession saved = practices.save(PracticeSession.firstStudy(currentUser.id(), learningSessionId, questionIds,
                clock.instant()));
        progress.markStudyStarted(learningSessionId);
        return view(saved);
    }

    /**
     * 지금 풀 문제. 현재 제시에 아직 답하지 않았으면 같은 제시를 다시 돌려주고(제시 시각 유지),
     * 답했으면 다음 문제를 제시하며 제시 시각과 예측 R을 남긴다.
     */
    @Transactional
    public Next next(Long practiceId) {
        PracticeSession practice = owned(practiceId);
        Optional<QuestionPresentation> current = presentations.findTopByPracticeSessionIdOrderByPositionDesc(practiceId);
        if (current.isPresent() && !attempts.existsByPresentationId(current.get().getId())) {
            return new Next(false, view(practice, current.get(), questions.question(current.get().getQuestionId())));
        }
        List<PracticeQueueEntry> queue = practice.getQueue();
        for (int position = current.map(p -> p.getPosition() + 1).orElse(0); position < queue.size(); position++) {
            PracticeQueueEntry entry = queue.get(position);
            Question question = questions.question(entry.getQuestionId());
            if (question.getStatus() != QuestionStatus.APPROVED) {
                continue;
            }
            QuestionPresentation presented = presentations.save(new QuestionPresentation(practiceId, practice.getUserId(),
                    position, question.getId(), question.getMemoryItemId(), question.getType(), clock.instant(),
                    memory.retrievability(question.getMemoryItemId()).orElse(null), entry.getRecheckOfPresentationId()));
            return new Next(false, view(practice, presented, question));
        }
        boolean firstCompletion = practice.getCompletedAt() == null;
        practice.complete(clock.instant());
        if (firstCompletion && practice.getKind() == PracticeKind.DAILY) {
            events.publishEvent(new DailyPracticeCompleted(practice.getUserId(), practice.getId(),
                    practice.getStartedAt().atZone(clock.getZone()).toLocalDate()));
        }
        return new Next(true, null);
    }

    /** 현재 제시에서 도움을 보여 줬다. 내용(힌트·설명)은 단계적 피드백이 내고, 여기서는 노출만 남긴다. */
    @Transactional
    public AidView recordAid(Long presentationId, AidType type) {
        if (type == null) {
            throw new IllegalArgumentException("도움 종류를 고르세요.");
        }
        QuestionPresentation presentation = currentPresentation(presentationId);
        AidExposure saved = aids.save(new AidExposure(presentation.getId(), type, clock.instant()));
        return new AidView(saved.getId(), saved.getType(), saved.getExposedAt());
    }

    /**
     * 답을 기록하고 바로 판정한 뒤 등급 변환과 FSRS 갱신까지 한다(스펙 §6.4.5). 객관식은 코드가 채점하고, 그 밖에는 Jev가 판정한다.
     * Jev 호출은 트랜잭션 밖에서 한다. 판정하지 못하면 등급 변환을 하지 않아 기억 상태도 보류 횟수도 바뀌지 않는다.
     */
    public AttemptView submit(Long presentationId, Submission submission) {
        Recorded recorded = transaction.execute(tx -> record(presentationId, submission));
        AnswerJudgment judgment = judge(recorded.attempt(), recorded.question());
        transaction.executeWithoutResult(tx -> judgments.save(judgment));
        PracticeAttempt attempt = recorded.attempt();
        RatingDecision decision = judgment.getStatus() == JudgmentStatus.JUDGED ? recordReview(attempt, judgment) : null;
        return new AttemptView(attempt.getId(), attempt.getKind(), AttemptRules.isEvaluated(attempt.getKind()),
                attempt.getChoiceCorrect(), attempt.getResponseTimeMs(), JudgmentView.of(judgment),
                decision instanceof RatingDecision.Rated rated ? rated.rating() : null,
                decision instanceof RatingDecision.Held held ? held.reason() : null);
    }

    /**
     * 판정된 시도를 등급 변환에 넘긴다. 신뢰도 기준은 등급 변환이 적용한다. 객관식은 읽기 어려운 시간 안에 고른 정답이면
     * 추측을 의심한다(변환표 행 3).
     */
    private RatingDecision recordReview(PracticeAttempt attempt, AnswerJudgment judgment) {
        Duration responseTime = Duration.ofMillis(attempt.getResponseTimeMs());
        boolean guessSuspected = attempt.getQuestionType() == QuestionType.MULTIPLE_CHOICE
                && judgment.getVerdict() == AnswerVerdict.MET && responseTime.compareTo(multipleChoiceGuessTime) < 0;
        RatingInput input = new RatingInput(attempt.getKind(), judgment.getVerdict(), judgment.getVerdictConfidence(),
                judgment.isMisread(), judgment.getMisreadConfidence() == null ? 0 : judgment.getMisreadConfidence(),
                guessSuspected, attempt.getSelfAssessment(), attempt.getQuestionType(), responseTime, judgment.isTranscribedEvidence());
        List<String> failures = judgment.getJudgedBy() == JudgedBy.CODE ? null : Stream.of(
                        judgment.isOmission() ? "omission" : null,
                        judgment.isContradiction() ? "contradiction" : null,
                        judgment.isMisread() ? "misread" : null)
                .filter(Objects::nonNull).toList();
        ReviewContext context = new ReviewContext(attempt.getUserId(), attempt.getMemoryItemId(), attempt.getQuestionId(),
                attempt.getId(), attempt.getSubmittedAt(), attempt.getPredictedRetrievability(), attempt.isPriorAidExposed(),
                attempt.getElapsedSincePriorMs(), attempt.isSameDayRecheck(), failures, attempt.getFirstInputMs());
        return reviews.record(context, input).decision();
    }

    private record Recorded(PracticeAttempt attempt, Question question) {
    }

    private Recorded record(Long presentationId, Submission submission) {
        QuestionPresentation presentation = currentPresentation(presentationId);
        Instant now = clock.instant();
        AttemptRules.Classification classification = AttemptRules.classify(history(presentation),
                Optional.ofNullable(presentation.getRecheckOfPresentationId()).map(this::history).orElse(null), now);
        if (AttemptRules.isEvaluated(classification.kind()) && submission.selfAssessment() == null) {
            throw new IllegalArgumentException("자기평가를 고르세요.");
        }
        Question question = questions.question(presentation.getQuestionId());
        PracticeAttempt.Answer answer = answer(question, submission);
        PracticeAttempt saved = attempts.save(PracticeAttempt.of(presentation, classification, answer,
                timing(submission, classification.timedFrom(), now)));
        return new Recorded(saved, question);
    }

    private AnswerJudgment judge(PracticeAttempt attempt, Question question) {
        LearningSessionDetail detail = sessions.detail(question.getSessionId());
        String fidelity = detail.fidelity();
        if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
            return AnswerJudgment.byCode(attempt.getId(), attempt.getChoiceCorrect(), fidelity, clock.instant());
        }
        AnswerJudge.Outcome outcome = judge.judge(state(detail, question, attempt.getAnswerText()));
        return outcome.judged()
                ? AnswerJudgment.byJev(attempt.getId(), outcome.jev(), judgePolicy.reasonBand(), fidelity, clock.instant())
                : AnswerJudgment.failed(attempt.getId(), JudgedBy.JEV, outcome.failure(), fidelity, clock.instant());
    }

    /** 헷갈린 지점 항목이면 항목 내용이 대화 속 `userBelief`이고, 출처 발화의 AI 교정을 함께 보낸다. */
    private static AnswerJudgeState state(LearningSessionDetail detail, Question question, String answer) {
        LearningSessionDetail.Item item = detail.units().stream().flatMap(unit -> unit.items().stream())
                .filter(i -> i.id().equals(question.getMemoryItemId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("기억 항목 %d 없음".formatted(question.getMemoryItemId())));
        boolean confusion = item.kind() == MemoryItemKind.CONFUSION;
        String correction = confusion ? detail.turns().stream()
                .filter(turn -> item.sourceTurns().contains(turn.index()) && turn.correction() != null)
                .map(LearningSessionDetail.Turn::correction).findFirst().orElse(null) : null;
        return new AnswerJudgeState(question.getStem(), question.getType().name(), question.getAnswerCriteria(),
                question.getModelAnswer(), answer, item.content(), confusion ? item.content() : null, correction);
    }

    private static PracticeAttempt.Answer answer(Question question, Submission submission) {
        if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
            Integer choice = submission.choiceIndex();
            if (choice == null || choice < 0 || choice >= question.getChoices().size()) {
                throw new IllegalArgumentException("선택지 번호는 0부터 %d 사이로 고르세요.".formatted(question.getChoices().size() - 1));
            }
            Boolean correct = question.getCorrectChoice() == null ? null : choice.equals(question.getCorrectChoice());
            return new PracticeAttempt.Answer(null, choice, correct, submission.selfAssessment());
        }
        String text = submission.answer() == null ? "" : submission.answer().strip();
        if (text.isEmpty() || text.length() > PracticeAttempt.MAX_ANSWER_LENGTH) {
            throw new IllegalArgumentException("답은 1~%d자로 적으세요.".formatted(PracticeAttempt.MAX_ANSWER_LENGTH));
        }
        return new PracticeAttempt.Answer(text, null, null, submission.selfAssessment());
    }

    /** 클라이언트가 잰 시간은 서버 경과 시간을 넘지 못한다. 첫 입력까지의 시간은 응답 시간을 넘지 못한다. */
    private static PracticeAttempt.Timing timing(Submission submission, Instant timedFrom, Instant now) {
        long server = Math.max(0, Duration.between(timedFrom, now).toMillis());
        Long client = submission.responseTimeMs();
        if ((client != null && client < 0) || (submission.firstInputMs() != null && submission.firstInputMs() < 0)) {
            throw new IllegalArgumentException("시간은 0 이상이어야 합니다.");
        }
        long response = client == null ? server : Math.min(client, server);
        Long firstInput = submission.firstInputMs() == null ? null : Math.min(submission.firstInputMs(), response);
        return new PracticeAttempt.Timing(now, response, server, firstInput);
    }

    private AttemptRules.History history(Long presentationId) {
        return history(presentations.findById(presentationId)
                .orElseThrow(() -> new PracticeNotFoundException("제시", presentationId)));
    }

    private AttemptRules.History history(QuestionPresentation presentation) {
        return new AttemptRules.History(presentation.getPresentedAt(),
                aids.findByPresentationIdOrderByExposedAtAscIdAsc(presentation.getId()).stream()
                        .map(aid -> new AttemptRules.Exposure(aid.getType(), aid.getExposedAt())).toList(),
                attempts.findByPresentationIdOrderBySubmittedAtAscIdAsc(presentation.getId()).stream()
                        .map(PracticeAttempt::getSubmittedAt).toList());
    }

    /** 도움과 답은 풀이 세션의 현재(마지막) 제시에만 받는다. */
    private QuestionPresentation currentPresentation(Long presentationId) {
        QuestionPresentation presentation = presentations.findById(presentationId)
                .filter(p -> p.getUserId().equals(currentUser.id()))
                .orElseThrow(() -> new PracticeNotFoundException("제시", presentationId));
        Long latest = presentations.findTopByPracticeSessionIdOrderByPositionDesc(presentation.getPracticeSessionId())
                .orElseThrow().getId();
        if (!latest.equals(presentationId)) {
            throw new IllegalStateException("제시 %d은(는) 이미 지나간 문제입니다.".formatted(presentationId));
        }
        return presentation;
    }

    private PracticeSession owned(Long practiceId) {
        return practices.findById(practiceId)
                .filter(p -> p.getUserId().equals(currentUser.id()))
                .orElseThrow(() -> new PracticeNotFoundException("풀이", practiceId));
    }

    private static PracticeView view(PracticeSession practice) {
        return new PracticeView(practice.getId(), practice.getKind(), practice.getLearningSessionId(),
                practice.getQueue().size(), practice.getCompletedAt() != null);
    }

    private static PresentationView view(PracticeSession practice, QuestionPresentation presentation, Question question) {
        return new PresentationView(presentation.getId(), presentation.getPosition(), practice.getQueue().size(),
                question.getId(), question.getType(), question.getStem(), question.getChoices(), presentation.isSameDayRecheck());
    }
}
