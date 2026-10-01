package com.khack.review.practice.application;

import com.khack.review.analysis.application.LearningSessionDetail;
import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.domain.AttemptKind;
import com.khack.review.memory.domain.AnswerVerdict;
import com.khack.review.memory.domain.HoldReason;
import com.khack.review.memory.domain.RatingDecision;
import com.khack.review.memory.domain.RatingInput;
import com.khack.review.memory.domain.RatingPolicy;
import com.khack.review.practice.application.NextActionJudge.DecidedBy;
import com.khack.review.practice.application.port.out.FeedbackContent;
import com.khack.review.practice.application.port.out.FeedbackContentGenerator;
import com.khack.review.practice.application.port.out.FeedbackContentRequest;
import com.khack.review.practice.application.port.out.PrerequisiteSuggestion;
import com.khack.review.practice.domain.AidExposure;
import com.khack.review.practice.domain.AidExposureRepository;
import com.khack.review.practice.domain.AidType;
import com.khack.review.practice.domain.AttemptOutcome;
import com.khack.review.practice.domain.AnswerJudgment;
import com.khack.review.practice.domain.AnswerJudgmentRepository;
import com.khack.review.practice.domain.JudgmentStatus;
import com.khack.review.practice.domain.FeedbackAction;
import com.khack.review.practice.domain.FeedbackPath;
import com.khack.review.practice.domain.FeedbackRules;
import com.khack.review.practice.domain.PracticeAttempt;
import com.khack.review.practice.domain.PracticeAttemptRepository;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.practice.domain.PresentationFeedback;
import com.khack.review.practice.domain.PresentationFeedbackRepository;
import com.khack.review.practice.domain.QuestionPresentation;
import com.khack.review.practice.domain.QuestionPresentationRepository;
import com.khack.review.question.application.QuestionGenerationService;
import com.khack.review.question.application.QuestionQueryService;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 단계적 피드백 (스펙 §7 5·6단계, §6.2, §6.4.8). 답한 제시의 시도 결과를 보고 다음 행동을 정해 실행한다.
 * 상태 기계({@link FeedbackRules})가 허용한 행동 중 둘 이상이면 Jev({@link NextActionJudge})가 고른다.
 * 힌트·설명 내용은 {@link FeedbackContentGenerator}(LLM)가 만들고, 없거나 실패하면 문제에 저장된 기본 힌트·설명을 쓴다.
 *
 * <p>이 서비스는 FSRS 등급·복습 기록을 건드리지 않는다. 힌트·설명은 도움 노출로만 남겨 이후 시도가 {@code ASSISTED_RETRY}로
 * 분류되게 하고(평가 제외), 오늘 다시 묻기는 큐 끝의 재확인 제시로 편성해 {@code DELAYED_RECHECK}로 평가되게 한다.
 * 확인 문제는 새 제시이므로 이전 답·힌트는 화면에 나오지 않는다. 큐 끝에 붙이므로 큐에 남은 다른 문제들 뒤에 나온다.
 *
 * <p>오래 걸리는 생성(LLM)·판정(Jev) 호출은 트랜잭션 밖에서 하고, 저장은 호출마다 짧게 한다.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final String GENERIC_HINT = "문제를 다시 읽고, 핵심 개념이 무엇인지 떠올려 보세요.";

    private final PracticeSessionRepository practices;
    private final QuestionPresentationRepository presentations;
    private final AidExposureRepository aids;
    private final PracticeAttemptRepository attempts;
    private final AnswerJudgmentRepository judgments;
    private final RatingPolicy ratingPolicy;
    private final PresentationFeedbackRepository feedbacks;
    private final PracticeService practice;
    private final QuestionQueryService questions;
    private final QuestionGenerationService questionGeneration;
    private final LearningSessionQueryService sessions;
    private final ObjectProvider<FeedbackContentGenerator> generator;
    private final NextActionJudge judge;
    private final FeedbackPolicy policy;
    private final CurrentUser currentUser;
    private final Clock clock;

    public FeedbackService(PracticeSessionRepository practices, QuestionPresentationRepository presentations,
            AidExposureRepository aids, PracticeAttemptRepository attempts, AnswerJudgmentRepository judgments, RatingPolicy ratingPolicy,
            PresentationFeedbackRepository feedbacks, PracticeService practice, QuestionQueryService questions,
            QuestionGenerationService questionGeneration, LearningSessionQueryService sessions,
            ObjectProvider<FeedbackContentGenerator> generator, NextActionJudge judge, FeedbackPolicy policy,
            CurrentUser currentUser, Clock clock) {
        this.practices = practices;
        this.presentations = presentations;
        this.aids = aids;
        this.attempts = attempts;
        this.judgments = judgments;
        this.ratingPolicy = ratingPolicy;
        this.feedbacks = feedbacks;
        this.practice = practice;
        this.questions = questions;
        this.questionGeneration = questionGeneration;
        this.sessions = sessions;
        this.generator = generator;
        this.judge = judge;
        this.policy = policy;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public record Prerequisite(String concept, String reason) {
    }

    /**
     * 피드백 결과. {@code action}은 방금 정한(실행한) 행동이고 읽기 전용 조회에서는 마지막 행동이다.
     * {@code hint}·{@code explanation}은 이 제시에서 보여 준 본문이다. {@code path}는 표시용이다.
     */
    public record FeedbackView(Long presentationId, @Nullable FeedbackAction action, @Nullable DecidedBy decidedBy,
            @Nullable String reason, @Nullable FeedbackPath path, @Nullable String hint, @Nullable String explanation,
            List<Integer> evidenceTurns, boolean recheckQueued, @Nullable Prerequisite prerequisite) {
    }

    /** 지금까지 이 제시에서 한 피드백을 읽는다. 아무것도 바꾸지 않는다. */
    public FeedbackView current(Long presentationId) {
        QuestionPresentation presentation = presentations.findById(presentationId)
                .filter(p -> p.getUserId().equals(currentUser.id()))
                .orElseThrow(() -> new PracticeNotFoundException("제시", presentationId));
        return feedbacks.findByPresentationId(presentation.getId())
                .map(fb -> view(fb, fb.getLastAction(), null, null))
                .orElseGet(() -> new FeedbackView(presentationId, null, null, null, null, null, null, List.of(), false, null));
    }

    /**
     * 마지막 시도의 결과로 다음 행동을 정하고 실행한다. 같은 상태에서 다시 부르면 이미 한 행동은 반복하지 않고 다음 단계로 넘어간다
     * (힌트를 보여 준 뒤 재도전 전이면 {@code RETRY}).
     */
    public FeedbackView decide(Long presentationId) {
        QuestionPresentation presentation = latestOwned(presentationId);
        PracticeSession session = practices.findById(presentation.getPracticeSessionId()).orElseThrow();
        List<PracticeAttempt> history = attempts.findByPresentationIdOrderBySubmittedAtAscIdAsc(presentationId);
        if (history.isEmpty()) {
            throw new IllegalStateException("답한 뒤에 피드백을 받을 수 있습니다.");
        }
        List<AttemptOutcome> results = outcomesOf(history);
        AttemptOutcome latest = results.getLast();
        if (latest == null) {
            throw new IllegalStateException("시도 %d은(는) 아직 판정 기록이 없습니다. 판정이 끝난 뒤에 피드백을 받을 수 있습니다."
                    .formatted(history.getLast().getId()));
        }
        PracticeAttempt latestAttempt = history.getLast();
        List<AidExposure> exposures = aids.findByPresentationIdOrderByExposedAtAscIdAsc(presentationId);
        PresentationFeedback feedback = feedbacks.findByPresentationId(presentationId)
                .orElseGet(() -> new PresentationFeedback(presentationId, presentation.getUserId(), presentation.getMemoryItemId()));
        boolean hintShown = exposures.stream().anyMatch(a -> a.getType() == AidType.HINT);
        boolean explanationShown = exposures.stream().anyMatch(a -> a.getType() == AidType.EXPLANATION);
        boolean aidAfterLatest = exposures.stream()
                .anyMatch(a -> a.getType().isContent() && a.getExposedAt().isAfter(latestAttempt.getSubmittedAt()));
        int wrongAttempts = (int) results.stream().filter(r -> r == AttemptOutcome.WRONG).count();
        FeedbackRules.State state = new FeedbackRules.State(session.getKind(), presentation.isSameDayRecheck(), latest,
                hintShown || explanationShown, hintShown, explanationShown, aidAfterLatest,
                feedback.isRelearnQueued() || session.hasRecheckOf(presentationId), relearnCapReached(session));
        FeedbackRules.Plan plan = FeedbackRules.plan(state);

        Question question = questions.question(presentation.getQuestionId());
        long difficultItems = feedbacks.countByUserIdAndMemoryItemIdAndWrongAttemptsGreaterThan(presentation.getUserId(),
                presentation.getMemoryItemId(), 0);
        NextActionJudge.Choice choice = judge.choose(nextActionState(session, presentation, question, history, results,
                state, difficultItems >= policy.repeatedDifficultyPresentations()), plan);
        log.info("피드백 제시 {}: {} ({}, {})", presentationId, choice.action(), choice.decidedBy(), choice.detail());

        perform(choice.action(), presentation, session, history, question, feedback);

        boolean hintBeforeLatest = exposures.stream().anyMatch(a -> a.getType() == AidType.HINT
                && !a.getExposedAt().isAfter(latestAttempt.getSubmittedAt()));
        boolean explanationBeforeLatest = exposures.stream().anyMatch(a -> a.getType() == AidType.EXPLANATION
                && !a.getExposedAt().isAfter(latestAttempt.getSubmittedAt()));
        feedback.observe(FeedbackRules.path(latest, wrongAttempts, hintBeforeLatest, explanationBeforeLatest), wrongAttempts,
                choice.action());
        feedback = feedbacks.save(feedback);
        if (latest == AttemptOutcome.WRONG) {
            feedback = suggestPrerequisiteIfRepeated(feedback, presentation, question, history);
        }
        return view(feedback, choice.action(), choice.decidedBy(), choice.detail());
    }

    private void perform(FeedbackAction action, QuestionPresentation presentation, PracticeSession session,
            List<PracticeAttempt> history, Question question, PresentationFeedback feedback) {
        switch (action) {
            case GIVE_HINT -> {
                FeedbackContent content = content(question, presentation, history, true);
                practice.recordAid(presentation.getId(), AidType.HINT);
                feedback.hintShown(content.text());
            }
            case EXPLAIN_CONCEPT -> {
                FeedbackContent content = content(question, presentation, history, false);
                practice.recordAid(presentation.getId(), AidType.EXPLANATION);
                feedback.explanationShown(content.text(), content.evidenceTurns());
                // 첫 학습은 설명 뒤에 확인 문제를 낸다. 매일 학습은 설명만 보여 주고 다시 묻기는 relearn_today가 한다.
                if (session.getKind() == PracticeKind.FIRST_STUDY) {
                    queueRelearn(session, presentation, question.getId(), feedback);
                }
            }
            case RELEARN_TODAY -> queueRelearn(session, presentation, question.getId(), feedback);
            case GENERATE_VARIANT -> {
                // 모호한 문제는 같은 문제를 다시 내지 않는다. 재검사해 쓸 수 있는 문제(수정·변형)로 오늘 안에 다시 확인한다.
                Optional<Question> usable = questionGeneration.recheck(question.getId());
                usable.ifPresentOrElse(q -> queueRelearn(session, presentation, q.getId(), feedback),
                        () -> log.info("제시 {}: 쓸 수 있는 변형 문제가 없어 이 항목 출제를 보류", presentation.getId()));
            }
            default -> {
            }
        }
    }

    /** 오늘 큐 끝에 다시 넣는다. 제시마다 한 번이고, 풀이 세션의 하루 분량 상한을 넘으면 넣지 않는다. */
    private void queueRelearn(PracticeSession stale, QuestionPresentation presentation, Long questionId,
            PresentationFeedback feedback) {
        PracticeSession session = practices.findById(stale.getId()).orElseThrow();
        if (feedback.isRelearnQueued() || session.hasRecheckOf(presentation.getId()) || relearnCapReached(session)) {
            return;
        }
        session.enqueueRecheck(questionId, presentation.getId());
        practices.save(session);
        feedback.relearnQueued();
    }

    private boolean relearnCapReached(PracticeSession session) {
        return session.recheckCount() >= policy.relearnMaxPerSession();
    }

    private PresentationFeedback suggestPrerequisiteIfRepeated(PresentationFeedback feedback, QuestionPresentation presentation,
            Question question, List<PracticeAttempt> history) {
        if (feedback.getPrerequisiteConcept() != null) {
            return feedback;
        }
        long difficult = feedbacks.countByUserIdAndMemoryItemIdAndWrongAttemptsGreaterThan(presentation.getUserId(),
                presentation.getMemoryItemId(), 0);
        FeedbackContentGenerator available = generator.getIfAvailable();
        if (difficult < policy.repeatedDifficultyPresentations() || available == null) {
            return feedback;
        }
        try {
            PrerequisiteSuggestion suggestion = available.prerequisite(request(question, presentation, history, null));
            feedback.suggestPrerequisite(suggestion.concept(), suggestion.reason());
            return feedbacks.save(feedback);
        } catch (RuntimeException e) {
            log.warn("선행 개념 제안 실패: {}", e.getMessage());
            return feedback;
        }
    }

    private FeedbackContent content(Question question, QuestionPresentation presentation, List<PracticeAttempt> history,
            boolean hint) {
        FeedbackContentGenerator available = generator.getIfAvailable();
        if (available != null) {
            try {
                FeedbackContentRequest request = request(question, presentation, history,
                        hint ? question.getHint() : question.getExplanation());
                return hint ? available.hint(request) : available.explanation(request);
            } catch (RuntimeException e) {
                log.warn("{} 생성 실패, 문제에 저장된 기본 {}으로 대신함: {}", hint ? "힌트" : "개념 설명", hint ? "힌트" : "설명", e.getMessage());
            }
        }
        String stored = hint ? question.getHint() : question.getExplanation();
        if (stored != null && !stored.isBlank()) {
            return new FeedbackContent(stored, question.getEvidenceTurns());
        }
        return new FeedbackContent(hint ? GENERIC_HINT : question.getModelAnswer(), question.getEvidenceTurns());
    }

    private FeedbackContentRequest request(Question question, QuestionPresentation presentation, List<PracticeAttempt> history,
            @Nullable String storedText) {
        LearningSessionDetail detail = sessions.detail(question.getSessionId());
        Map<Integer, LearningSessionDetail.Turn> turns = detail.turns().stream()
                .collect(Collectors.toMap(LearningSessionDetail.Turn::index, Function.identity(), (a, b) -> a));
        LearningSessionDetail.Item item = detail.units().stream().flatMap(unit -> unit.items().stream())
                .filter(i -> i.id().equals(presentation.getMemoryItemId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("기억 항목 %d을(를) 찾을 수 없습니다.".formatted(presentation.getMemoryItemId())));
        TreeSet<Integer> indexes = new TreeSet<>(item.sourceTurns());
        indexes.addAll(question.getEvidenceTurns());
        List<FeedbackContentRequest.EvidenceTurn> evidence = indexes.stream().map(turns::get).filter(t -> t != null)
                .map(t -> new FeedbackContentRequest.EvidenceTurn(t.index(), t.text(),
                        t.aiVerdict() == null ? null : t.aiVerdict().name(), t.correction()))
                .toList();
        boolean confusion = item.kind() == MemoryItemKind.CONFUSION;
        String correction = confusion ? evidence.stream().map(FeedbackContentRequest.EvidenceTurn::correction)
                .filter(c -> c != null).findFirst().orElse(null) : null;
        List<String> previous = new ArrayList<>();
        for (PracticeAttempt attempt : history) {
            if (attempt.getQuestionType() == QuestionType.MULTIPLE_CHOICE && attempt.getChoiceIndex() != null) {
                previous.add(question.getChoices().get(attempt.getChoiceIndex()));
            } else if (attempt.getAnswerText() != null) {
                previous.add(attempt.getAnswerText());
            }
        }
        return new FeedbackContentRequest(question.getStem(), question.getType(), question.getChoices(),
                question.getAnswerCriteria(), question.getModelAnswer(), item.kind(), item.content(),
                confusion ? item.content() : null, correction, evidence, previous, storedText);
    }

    private NextActionState nextActionState(PracticeSession session, QuestionPresentation presentation, Question question,
            List<PracticeAttempt> history, List<AttemptOutcome> results, FeedbackRules.State state, boolean repeatedDifficulty) {
        List<NextActionState.Step> steps = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            AttemptKind kind = history.get(i).getKind();
            steps.add(new NextActionState.Step(kind, results.get(i)));
        }
        return new NextActionState(session.getKind(), presentation.isSameDayRecheck(), question.getType(), question.getStem(),
                state.outcome(), steps, state.hintShown(), state.explanationShown(), repeatedDifficulty);
    }

    /**
     * 시도별 결과. 이미 저장된 답변 판정에서 도출하므로 따로 기록하지 않는다. 판정이 없으면 null.
     * 객관식 코드 채점과 서술형 Jev 판정이 같은 {@link AnswerJudgment}로 들어온다.
     */
    private List<AttemptOutcome> outcomesOf(List<PracticeAttempt> history) {
        Map<Long, AnswerJudgment> byAttempt = judgments.findByAttemptIdIn(history.stream().map(PracticeAttempt::getId).toList())
                .stream().collect(Collectors.toMap(AnswerJudgment::getAttemptId, Function.identity()));
        List<AttemptOutcome> results = new ArrayList<>();
        for (PracticeAttempt attempt : history) {
            AnswerJudgment judgment = byAttempt.get(attempt.getId());
            results.add(judgment == null ? null : outcome(attempt, judgment));
        }
        return results;
    }

    /**
     * 판정 → 피드백 입력 (스펙 §6.4.5). 판정 실패·신뢰도 미달은 UNCERTAIN(등급 변환이 보류한 경우와 같다), 판정 불가·질문 오해는
     * 문제가 모호한 것이라 QUESTION_AMBIGUOUS, 나머지는 verdict대로다. 신뢰도 기준은 등급 변환 정책에 맡기며, 도움 후 재시도는
     * 등급 변환을 거치지 않으므로 첫 무도움 시도로 가정해 같은 정책에 물어본다. 추측 의심 보류는 맞힌 것이므로 CORRECT다.
     */
    private AttemptOutcome outcome(PracticeAttempt attempt, AnswerJudgment judgment) {
        if (judgment.getStatus() == JudgmentStatus.FAILED) {
            return AttemptOutcome.UNCERTAIN;
        }
        RatingInput input = new RatingInput(AttemptKind.FIRST_UNASSISTED, judgment.getVerdict(), judgment.getVerdictConfidence(),
                judgment.isMisread(), judgment.getMisreadProbability() == null ? 0 : judgment.getMisreadProbability(), false,
                attempt.getSelfAssessment(), attempt.getQuestionType(), null, judgment.isEvidenceTranscribed());
        if (ratingPolicy.decide(input) instanceof RatingDecision.Held held) {
            return held.reason() == HoldReason.LOW_CONFIDENCE ? AttemptOutcome.UNCERTAIN : AttemptOutcome.QUESTION_AMBIGUOUS;
        }
        return judgment.getVerdict() == AnswerVerdict.MET ? AttemptOutcome.CORRECT : AttemptOutcome.WRONG;
    }

    /** 피드백은 세션의 현재(마지막) 제시에만 받는다. */
    private QuestionPresentation latestOwned(Long presentationId) {
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

    private static FeedbackView view(PresentationFeedback fb, @Nullable FeedbackAction action, @Nullable DecidedBy decidedBy,
            @Nullable String reason) {
        Prerequisite prerequisite = fb.getPrerequisiteConcept() == null ? null
                : new Prerequisite(fb.getPrerequisiteConcept(), fb.getPrerequisiteReason());
        return new FeedbackView(fb.getPresentationId(), action, decidedBy, reason, fb.getPath(), fb.getHintText(),
                fb.getExplanationText(), fb.getExplanationEvidenceTurns(), fb.isRelearnQueued(), prerequisite);
    }
}
