package com.khack.review.practice.application;

import com.khack.review.analysis.application.SessionProgressService;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.MemoryStateService;
import com.khack.review.practice.domain.AidExposure;
import com.khack.review.practice.domain.AidExposureRepository;
import com.khack.review.practice.domain.AidType;
import com.khack.review.practice.domain.AttemptKind;
import com.khack.review.practice.domain.AttemptRules;
import com.khack.review.practice.domain.PracticeAttempt;
import com.khack.review.practice.domain.PracticeAttemptRepository;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.practice.domain.PracticeQueueEntry;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.practice.domain.QuestionPresentation;
import com.khack.review.practice.domain.QuestionPresentationRepository;
import com.khack.review.practice.domain.SelfAssessment;
import com.khack.review.question.application.QuestionQueryService;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 풀이 진행과 답변 제출 (스펙 §6.4.5, §9.4). 문제를 제시하고 답과 시도 정보를 기록한다.
 * 서술형 판정(Jev), 등급 변환, FSRS 갱신, 힌트·설명 내용은 여기서 하지 않는다. 정답은 응답에 넣지 않는다.
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
    private final CurrentUser currentUser;
    private final Clock clock;

    public PracticeService(PracticeSessionRepository practices, QuestionPresentationRepository presentations,
            AidExposureRepository aids, PracticeAttemptRepository attempts, SessionProgressService progress, FirstStudyQueryService firstStudy, QuestionQueryService questions,
            MemoryStateService memory, CurrentUser currentUser, Clock clock) {
        this.practices = practices;
        this.presentations = presentations;
        this.aids = aids;
        this.attempts = attempts;
        this.progress = progress;
        this.firstStudy = firstStudy;
        this.questions = questions;
        this.memory = memory;
        this.currentUser = currentUser;
        this.clock = clock;
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
    public record AttemptView(Long attemptId, AttemptKind kind, boolean evaluated, @Nullable Boolean correct,
            long responseTimeMs) {
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
        practice.complete(clock.instant());
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

    @Transactional
    public AttemptView submit(Long presentationId, Submission submission) {
        QuestionPresentation presentation = currentPresentation(presentationId);
        Instant now = clock.instant();
        AttemptRules.Classification classification = AttemptRules.classify(history(presentation),
                Optional.ofNullable(presentation.getRecheckOfPresentationId()).map(this::history).orElse(null), now);
        if (classification.kind().isEvaluated() && submission.selfAssessment() == null) {
            throw new IllegalArgumentException("자기평가를 고르세요.");
        }
        PracticeAttempt.Answer answer = answer(questions.question(presentation.getQuestionId()), submission);
        PracticeAttempt saved = attempts.save(PracticeAttempt.of(presentation, classification, answer,
                timing(submission, classification.timedFrom(), now)));
        return new AttemptView(saved.getId(), saved.getKind(), saved.getKind().isEvaluated(), saved.getChoiceCorrect(),
                saved.getResponseTimeMs());
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
