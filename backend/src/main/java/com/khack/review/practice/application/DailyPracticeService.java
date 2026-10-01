package com.khack.review.practice.application;

import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.DailyQueueService;
import com.khack.review.memory.application.RatingPolicyProperties;
import com.khack.review.memory.domain.DailyQueuePlanner;
import com.khack.review.memory.domain.NewItemTypes;
import com.khack.review.practice.domain.FirstStudyPlan;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.NewItemGoalTypes;
import com.khack.review.practice.domain.PracticeKind;
import com.khack.review.practice.domain.PracticeQueueEntry;
import com.khack.review.practice.domain.PracticeSession;
import com.khack.review.practice.domain.PracticeSessionRepository;
import com.khack.review.practice.domain.QuestionPresentation;
import com.khack.review.practice.domain.QuestionPresentationRepository;
import com.khack.review.question.application.QuestionGenerationService;
import com.khack.review.question.application.QuestionQueryService;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 학습 조회와 시작 (스펙 §6.4.4, §7.7, 도메인 스토리 S2-1~4). 큐 계산은 memory의 {@link DailyQueueService}가 하고,
 * 여기서는 항목마다 낼 문제를 고르고(없으면 변형 요청) 매일 학습 풀이 세션을 만든다. 풀이 진행·제출은 {@link PracticeService}를 그대로 쓴다.
 * 문제를 만드는 동안 트랜잭션을 잡지 않도록 이 서비스는 트랜잭션 밖에서 동작하고 저장만 짧게 한다.
 * 하루(시계 기준 날짜)에 풀이 세션은 하나이며, 다시 시작하면 오늘 세션을 이어서 연다.
 */
@Service
public class DailyPracticeService {

    private final DailyQueueService dailyQueue;
    private final PracticeSessionRepository practices;
    private final QuestionPresentationRepository presentations;
    private final FirstStudyPlanRepository plans;
    private final QuestionQueryService questions;
    private final QuestionGenerationService generation;
    private final RatingPolicyProperties rating;
    private final CurrentUser currentUser;
    private final Clock clock;

    public DailyPracticeService(DailyQueueService dailyQueue, PracticeSessionRepository practices,
            QuestionPresentationRepository presentations, FirstStudyPlanRepository plans, QuestionQueryService questions,
            QuestionGenerationService generation, RatingPolicyProperties rating, CurrentUser currentUser, Clock clock) {
        this.dailyQueue = dailyQueue;
        this.practices = practices;
        this.presentations = presentations;
        this.plans = plans;
        this.questions = questions;
        this.generation = generation;
        this.rating = rating;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * 큐 항목 1개. {@code source}는 복습/신규, {@code retrievability}는 지금 R(첫 등급 전이면 null)이다.
     * 문제 내용은 시작 전에 보이지 않는다.
     */
    public record ItemView(Long memoryItemId, Long learningSessionId, MemoryItemKind kind, QuestionType questionType,
            DailyQueuePlanner.Source source, boolean held, boolean relearnToday, @Nullable Double retrievability,
            long estimatedSeconds) {
    }

    /**
     * 오늘의 학습. 시작 전이면 {@code practiceId}가 null이고 {@code items}가 오늘 만들 큐 미리보기다. 시작한 뒤에는 {@code practiceId}로 풀고
     * {@code total}과 예상 시간은 만든 문제 기준이며 {@code items}는 비어 있다.
     * {@code carriedOver}는 예산·하루 1개·신규 상한으로 다음 큐로 넘어간 후보 수, {@code unavailable}은 문제를 만들 수 없어 뺀 항목이다.
     */
    public record DailyView(LocalDate date, @Nullable Long practiceId, boolean started, boolean completed, int total,
            int reviewCount, int newCount, long estimatedSeconds, List<ItemView> items, int carriedOver, List<Long> unavailable) {
    }

    @Transactional(readOnly = true)
    public DailyView today() {
        Long userId = currentUser.id();
        Optional<PracticeSession> existing = todaysSession(userId);
        if (existing.isPresent()) {
            return startedView(existing.get(), List.of());
        }
        DailyQueueService.DailyQueue queue = dailyQueue.preview(userId, newTypes(), Set.of());
        return previewView(queue, null, List.of());
    }

    /**
     * 오늘의 학습을 시작한다. 이미 시작했으면 그 풀이를 이어서 연다. 큐를 만들고(`DailyQueueBuilt`) 항목마다 승인 문제를 고르며,
     * 승인 문제가 없으면 변형(신규 항목이면 새) 문제를 요청한다. 만들지 못한 항목은 뺀다. 낼 문제가 없으면 풀이를 만들지 않는다.
     */
    public DailyView start() {
        Long userId = currentUser.id();
        Optional<PracticeSession> existing = todaysSession(userId);
        if (existing.isPresent()) {
            return startedView(existing.get(), List.of());
        }
        DailyQueueService.DailyQueue queue = dailyQueue.build(userId, newTypes(), Set.of());
        List<Long> questionIds = new ArrayList<>();
        List<DailyQueuePlanner.Entry> chosen = new ArrayList<>();
        List<Long> unavailable = new ArrayList<>();
        for (DailyQueuePlanner.Entry entry : queue.plan().entries()) {
            Optional<Question> question = pick(userId, entry);
            if (question.isPresent()) {
                questionIds.add(question.get().getId());
                chosen.add(entry);
            } else {
                unavailable.add(entry.itemId());
            }
        }
        if (questionIds.isEmpty()) {
            return previewView(queue, null, unavailable);
        }
        PracticeSession saved = practices.save(PracticeSession.daily(userId, questionIds, clock.instant()));
        DailyPracticeSummary summary = summarize(chosen);
        return new DailyView(queue.date(), saved.getId(), true, false, questionIds.size(), summary.review(), summary.newItems(),
                summary.seconds(), List.of(), queue.plan().carriedOver().size(), List.copyOf(unavailable));
    }

    private record DailyPracticeSummary(int review, int newItems, long seconds) {
    }

    private static DailyPracticeSummary summarize(List<DailyQueuePlanner.Entry> entries) {
        return new DailyPracticeSummary((int) entries.stream().filter(e -> e.source() == DailyQueuePlanner.Source.REVIEW).count(),
                (int) entries.stream().filter(e -> e.source() == DailyQueuePlanner.Source.NEW).count(),
                entries.stream().mapToLong(e -> e.estimated().toSeconds()).sum());
    }

    /**
     * 항목에 낼 문제. 정해진 유형의 승인 문제 중 아직 본 적 없는 것을 먼저 쓰고(모호해서 보류된 문제는 제외), 없으면 변형을 요청한다.
     * 변형도 못 만들면 더 낮거나 같은 단계의 승인 문제로 대신하고, 그것도 없으면 비어 있다.
     */
    private Optional<Question> pick(Long userId, DailyQueuePlanner.Entry entry) {
        Set<Long> seen = presentations.findByUserIdAndMemoryItemId(userId, entry.itemId()).stream()
                .map(QuestionPresentation::getQuestionId).collect(Collectors.toSet());
        List<Question> approved = questions.approvedOf(entry.itemId()).stream()
                .filter(q -> !q.getId().equals(entry.avoidQuestionId())).toList();
        Optional<Question> sameType = preferUnseen(approved.stream().filter(q -> q.getType() == entry.type()).toList(), seen);
        if (sameType.isPresent()) {
            return sameType;
        }
        Optional<Question> generated = generation.requestForItem(entry.sessionId(), entry.itemId(), entry.type());
        if (generated.isPresent()) {
            return generated;
        }
        return preferUnseen(approved.stream().filter(q -> q.getType().ladderLevel() <= entry.type().ladderLevel()).toList(), seen);
    }

    private static Optional<Question> preferUnseen(List<Question> candidates, Set<Long> seen) {
        return candidates.stream().filter(q -> !seen.contains(q.getId())).findFirst().or(() -> candidates.stream().findFirst());
    }

    private Optional<PracticeSession> todaysSession(Long userId) {
        Instant dayStart = clock.instant().atZone(clock.getZone()).toLocalDate().atStartOfDay(clock.getZone()).toInstant();
        return practices.findFirstByUserIdAndKindAndStartedAtGreaterThanEqualOrderByIdAsc(userId, PracticeKind.DAILY, dayStart);
    }

    private DailyView startedView(PracticeSession session, List<Long> unavailable) {
        List<Question> queued = session.getQueue().stream().map(PracticeQueueEntry::getQuestionId).map(questions::question).toList();
        long seconds = queued.stream().mapToLong(q -> rating.referenceTimes().get(q.getType()).toSeconds()).sum();
        LocalDate date = session.getStartedAt().atZone(clock.getZone()).toLocalDate();
        return new DailyView(date, session.getId(), true, session.getCompletedAt() != null, queued.size(), 0, 0, seconds, List.of(),
                0, unavailable);
    }

    private static DailyView previewView(DailyQueueService.DailyQueue queue, @Nullable Long practiceId, List<Long> unavailable) {
        DailyQueuePlanner.Plan plan = queue.plan();
        List<ItemView> items = plan.entries().stream().map(e -> new ItemView(e.itemId(), e.sessionId(), e.kind(), e.type(),
                e.source(), e.held(), e.relearnToday(), e.retrievability(), e.estimated().toSeconds())).toList();
        return new DailyView(queue.date(), practiceId, false, false, items.size(), plan.reviewCount(), plan.newCount(),
                plan.estimatedTime().toSeconds(), items, plan.carriedOver().size(), List.copyOf(unavailable));
    }

    /** 신규 항목의 문제 유형은 세션의 학습 목표(첫 학습 계획)로 정한다. */
    private NewItemTypes newTypes() {
        Map<Long, Optional<FirstStudyPlan>> cache = new HashMap<>();
        return (sessionId, itemId, kind) -> NewItemGoalTypes.typeFor(
                cache.computeIfAbsent(sessionId, plans::findBySessionId).orElse(null), itemId, kind);
    }
}
