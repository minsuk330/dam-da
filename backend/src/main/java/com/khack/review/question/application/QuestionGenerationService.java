package com.khack.review.question.application;

import com.khack.review.analysis.application.LearningSessionDetail;
import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.application.SessionProgressService;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.analysis.domain.MemoryItemStatus;
import com.khack.review.question.application.port.out.GeneratedQuestion;
import com.khack.review.question.application.port.out.QuestionGenerator;
import com.khack.review.question.application.port.out.UnitQuestionRequest;
import com.khack.review.question.application.port.out.UnitQuestionResult;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionSpec;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 문제 생성·품질 검사·변형 (스펙 §6.1, §6.2, 규칙 3·7·12). 문제는 복습 단위별로 한 번에 생성하고(포트 계약),
 * 떨어진 대상만 모아 다시 생성한다. 생성(LLM)과 검사(Jev)는 오래 걸리므로 트랜잭션 밖에서 하고, 저장과 상태 변경만
 * 짧은 트랜잭션으로 묶는다. 한 대상은 {@code max-generations}번까지 생성하고, 모두 떨어지면 그 항목 출제를 보류한다.
 *
 * <p>첫 학습 문제는 복습 단위들을 동시에 만든다(단위끼리는 기억 항목이 겹치지 않아 중복 검사가 서로 얽히지 않는다). 한 단위 안은
 * 지금처럼 차례로 검사한다. 목표를 다시 골라 계획이 바뀌면 이전 생성은 다음 요청 전에 멈추고 문제를 승인하지 않는다.
 */
@Service
public class QuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationService.class);
    private static final String SUPERSEDED = "학습 목표를 다시 골라 지난 계획의 문제를 쓰지 않음";

    private final ObjectProvider<QuestionGenerator> generator;
    private final QuestionQualityJudge judge;
    private final QuestionQualityPolicy policy;
    private final QuestionRepository questions;
    private final LearningSessionQueryService sessions;
    private final SessionProgressService progress;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final ExecutorService units;
    private final ExecutorService judges;

    public QuestionGenerationService(ObjectProvider<QuestionGenerator> generator, QuestionQualityJudge judge,
            QuestionQualityPolicy policy, QuestionRepository questions, LearningSessionQueryService sessions,
            SessionProgressService progress, Clock clock, TransactionTemplate transaction,
            @Value("${review.question.parallel-units:4}") int parallelUnits,
            @Value("${review.question.parallel-judges:8}") int parallelJudges) {
        this.generator = generator;
        this.judge = judge;
        this.policy = policy;
        this.questions = questions;
        this.sessions = sessions;
        this.progress = progress;
        this.clock = clock;
        this.transaction = transaction;
        AtomicInteger thread = new AtomicInteger();
        this.units = pool(parallelUnits, "qgen-", thread);
        // 단위 작업이 검사를 맡기고 기다리므로 단위 풀과 따로 둔다(같은 풀이면 단위가 풀을 다 차지할 때 검사가 돌지 못한다).
        this.judges = pool(parallelJudges, "qjudge-", thread);
    }

    private static ExecutorService pool(int size, String prefix, AtomicInteger thread) {
        return Executors.newFixedThreadPool(Math.max(1, size), task -> {
            Thread t = new Thread(task, prefix + thread.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        units.shutdownNow();
        judges.shutdownNow();
    }

    /**
     * 첫 학습 문제 생성 결과. {@code skipped}면 세션이 확인 완료 상태가 아니라 만들지 않았다.
     * 승인된 문제가 없으면 {@code failure}에 이유가 있다.
     */
    public record FirstStudyOutcome(boolean skipped, int planned, int approved, @Nullable String failure) {
    }

    public FirstStudyOutcome generateFirstStudy(Long sessionId, List<QuestionSpec> specs) {
        return generateFirstStudy(sessionId, specs, () -> true);
    }

    /**
     * 첫 학습 계획의 문제를 만든다. 확인 완료 상태의 세션만 하며(규칙 12), 승인된 문제가 하나라도 있으면 세션을 문제 준비로 넘긴다.
     * 승인되지 못한 자리는 출제를 보류한다(그 항목은 신규로 남는다). 하나도 승인되지 않으면 세션을 확인 완료에 두어
     * 목표를 다시 고를 수 있게 한다.
     *
     * @param current 이 생성이 아직 현재 계획의 것인가. 목표를 다시 골라 거짓이 되면 다음 생성 요청 전에 멈추고, 문제를 승인하지 않으며,
     *                세션을 문제 준비로 넘기지 않는다(새 계획의 생성이 넘긴다)
     */
    public FirstStudyOutcome generateFirstStudy(Long sessionId, List<QuestionSpec> specs, BooleanSupplier current) {
        LearningSessionDetail detail = sessions.detailForProcessing(sessionId);
        if (detail.status() != LearningSessionStatus.CONFIRMED || !current.getAsBoolean()) {
            log.info("학습 세션 {}: {} 상태이거나 지난 계획이라 첫 학습 문제를 만들지 않음", sessionId, detail.status());
            return new FirstStudyOutcome(true, specs.size(), 0, null);
        }
        releasePreviousPlan(sessionId);
        Context context = Context.of(detail, sessions.ownerOf(sessionId));
        Map<Long, List<Slot>> byUnit = new LinkedHashMap<>();
        for (QuestionSpec spec : specs) {
            byUnit.computeIfAbsent(context.item(spec.memoryItemId()).unitId(), unit -> new ArrayList<>())
                    .add(new Slot("p" + spec.position(), spec, null));
        }
        List<CompletableFuture<Integer>> work = byUnit.entrySet().stream()
                .map(unit -> CompletableFuture.supplyAsync(() -> (int) generate(context, unit.getKey(), unit.getValue(), current)
                        .values().stream().filter(Optional::isPresent).count(), units))
                .toList();
        int approved = 0;
        for (CompletableFuture<Integer> unit : work) {
            try {
                approved += unit.join();
            } catch (CompletionException e) {
                log.warn("학습 세션 {} 단위 문제 생성 실패", sessionId, e.getCause());
            }
        }
        if (!current.getAsBoolean()) {
            log.info("학습 세션 {}: 생성 중에 학습 목표를 다시 골라 이 생성 결과를 쓰지 않음", sessionId);
            return new FirstStudyOutcome(true, specs.size(), 0, null);
        }
        log.info("학습 세션 {} 첫 학습 문제: 계획 {}, 승인 {}, 보류 {}", sessionId, specs.size(), approved, specs.size() - approved);
        if (approved == 0) {
            String failure = specs.isEmpty() ? "계획된 문제가 없습니다."
                    : generator.getIfAvailable() == null ? "문제 생성기가 구성되지 않았습니다."
                    : "품질 검사를 통과한 문제가 없습니다.";
            return new FirstStudyOutcome(false, specs.size(), 0, failure + " 학습 목표를 다시 고르면 다시 만듭니다.");
        }
        progress.markQuestionsReady(sessionId);
        return new FirstStudyOutcome(false, specs.size(), approved, null);
    }

    /** 같은 기억 항목·유형의 변형 문제(규칙 7). 기존 문제와 다른 표현을 요청하며, 기억 상태는 항목 단위라 그대로 공유된다. */
    public Optional<Question> requestVariant(Long questionId) {
        Question original = questions.findById(questionId).orElseThrow(() -> new IllegalArgumentException("문제 없음: " + questionId));
        Context context = context(original.getSessionId());
        Slot slot = new Slot("v" + original.getId(), new QuestionSpec(-1, original.getMemoryItemId(), null, original.getType(), null),
                original.getId());
        return generate(context, context.item(original.getMemoryItemId()).unitId(), List.of(slot), () -> true).get(slot.targetId());
    }

    /**
     * 기억 항목에 지정한 유형의 문제를 새로 만든다(매일 학습에서 쓸 승인 문제가 없을 때, 스펙 §6.4.4). 항목에 이미 승인된
     * 문제가 있으면 그 문제의 변형으로, 없으면(신규 항목) 새 문제로 요청한다. 품질 검사를 통과해야 승인되며 못 만들면 비어 있다.
     */
    public Optional<Question> requestForItem(Long sessionId, Long memoryItemId, QuestionType type) {
        Context context = context(sessionId);
        Long variantOf = questions.findByMemoryItemIdAndStatusOrderByIdAsc(memoryItemId, QuestionStatus.APPROVED).stream()
                .map(Question::getId).findFirst().orElse(null);
        Slot slot = new Slot("d" + memoryItemId, new QuestionSpec(-1, memoryItemId, null, type, null), variantOf);
        return generate(context, context.item(memoryItemId).unitId(), List.of(slot), () -> true).get(slot.targetId());
    }

    /**
     * 풀이에서 모호하다고 보류된 승인 문제를 다시 검사한다(스펙 §6.4.5 보류 처리). 통과하면 그대로 쓰고,
     * 떨어지면 폐기하고 변형 문제를 만든다. 쓸 수 있는 문제를 돌려준다.
     */
    public Optional<Question> recheck(Long questionId) {
        Question question = questions.findById(questionId).orElseThrow(() -> new IllegalArgumentException("문제 없음: " + questionId));
        Context context = context(question.getSessionId());
        QualityOutcome outcome = judge.judge(state(context, question.getMemoryItemId(), content(question),
                approvedStems(question.getMemoryItemId(), question.getId())));
        if (outcome.approved()) {
            return Optional.of(question);
        }
        transaction.executeWithoutResult(tx -> questions.findById(questionId).orElseThrow().retire("재검사 탈락: " + outcome.note()));
        return requestVariant(questionId);
    }

    public List<Question> questionsOf(Long sessionId) {
        return questions.findBySessionIdOrderByIdAsc(sessionId);
    }

    /** 만들 문제 1개. {@code targetId}로 생성 결과를 맞춘다. */
    private record Slot(String targetId, QuestionSpec spec, @Nullable Long variantOf) {
    }

    /** 한 복습 단위의 대상들을 생성 → 검사하고, 떨어진 대상만 모아 다시 생성한다. 대상마다 승인된 문제 또는 빈 값. */
    private Map<String, Optional<Question>> generate(Context context, Long unitId, List<Slot> slots, BooleanSupplier current) {
        Map<String, Optional<Question>> outcome = new LinkedHashMap<>();
        slots.forEach(slot -> outcome.put(slot.targetId(), Optional.empty()));
        QuestionGenerator available = generator.getIfAvailable();
        if (available == null) {
            log.warn("문제 생성기가 구성되지 않아 단위 {}의 문제 {}개 출제를 보류", unitId, slots.size());
            return outcome;
        }
        Map<String, List<String>> tried = new HashMap<>();
        slots.forEach(slot -> tried.put(slot.targetId(), new ArrayList<>(stemsOf(slot.spec().memoryItemId()))));
        List<Slot> pending = new ArrayList<>(slots);
        for (int round = 1; round <= policy.maxGenerations() && !pending.isEmpty() && current.getAsBoolean(); round++) {
            Map<String, UnitQuestionResult.TargetResult> results = request(available, context, unitId, pending, tried, round);
            List<Slot> next = new ArrayList<>();
            Map<Long, List<Slot>> byItem = new LinkedHashMap<>();
            for (Slot slot : pending) {
                UnitQuestionResult.TargetResult result = results.get(slot.targetId());
                if (result == null || result.question() == null) {
                    log.info("단위 {} 대상 {} 생성 실패 ({}/{}): {}", unitId, slot.targetId(), round, policy.maxGenerations(),
                            result == null ? "결과 없음" : result.failure());
                    next.add(slot);
                    continue;
                }
                byItem.computeIfAbsent(slot.spec().memoryItemId(), item -> new ArrayList<>()).add(slot);
            }
            Map<String, Question> decidedBySlot = judgeByItem(context, byItem, results, round, current);
            for (Slot slot : pending) {
                Question decided = decidedBySlot.get(slot.targetId());
                if (decided == null) {
                    continue;
                }
                if (decided.getStatus() == QuestionStatus.APPROVED) {
                    outcome.put(slot.targetId(), Optional.of(decided));
                } else {
                    tried.get(slot.targetId()).add(decided.getStem());
                    next.add(slot);
                }
            }
            pending = next;
        }
        return outcome;
    }

    /**
     * 생성된 문제들을 저장하고 품질 검사한다. 기억 항목이 다른 문제끼리는 동시에 검사하고, 같은 항목의 문제는 차례로 검사한다.
     * 중복 검사는 같은 항목의 승인 문제와 비교하므로, 같은 항목을 동시에 검사하면 서로를 보지 못해 중복이 통과할 수 있다.
     */
    private Map<String, Question> judgeByItem(Context context, Map<Long, List<Slot>> byItem,
            Map<String, UnitQuestionResult.TargetResult> results, int round, BooleanSupplier current) {
        List<CompletableFuture<Map<String, Question>>> work = byItem.values().stream()
                .map(group -> CompletableFuture.supplyAsync(() -> {
                    Map<String, Question> decided = new LinkedHashMap<>();
                    for (Slot slot : group) {
                        decided.put(slot.targetId(), saveAndJudge(context, slot, results.get(slot.targetId()).question(), round, current));
                    }
                    return decided;
                }, judges))
                .toList();
        Map<String, Question> decided = new HashMap<>();
        for (CompletableFuture<Map<String, Question>> group : work) {
            try {
                decided.putAll(group.join());
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException runtime ? runtime : e;
            }
        }
        return decided;
    }

    private Map<String, UnitQuestionResult.TargetResult> request(QuestionGenerator available, Context context, Long unitId,
            List<Slot> pending, Map<String, List<String>> tried, int round) {
        UnitQuestionRequest request = context.request(unitId, pending.stream()
                .map(slot -> context.target(slot.targetId(), slot.spec(), tried.get(slot.targetId()))).toList());
        try {
            return available.generate(request).results().stream()
                    .collect(Collectors.toMap(UnitQuestionResult.TargetResult::targetId, Function.identity(), (first, later) -> first));
        } catch (RuntimeException e) {
            log.warn("단위 {} 문제 생성 요청 실패 ({}/{}): {}", unitId, round, policy.maxGenerations(), e.getMessage());
            return Map.of();
        }
    }

    private Question saveAndJudge(Context context, Slot slot, GeneratedQuestion generated, int round, BooleanSupplier current) {
        QuestionSpec spec = slot.spec();
        Question.Content content = new Question.Content(spec.type(), generated.stem(), generated.choices(),
                generated.correctChoice(), generated.answerCriteria(), generated.modelAnswer(), generated.hint(),
                generated.explanation(), generated.evidenceTurns());
        Long candidateId = transaction.execute(tx -> questions.save(Question.candidate(context.userId(), context.sessionId(),
                spec.memoryItemId(), spec.position() < 0 ? null : spec.position(), spec.learningGoal(), slot.variantOf(), round,
                content, clock.instant())).getId());
        QualityOutcome outcome = judge.judge(state(context, spec.memoryItemId(), content, approvedStems(spec.memoryItemId(), candidateId)));
        return transaction.execute(tx -> {
            Question candidate = questions.findById(candidateId).orElseThrow();
            if (!current.getAsBoolean()) {
                // 검사하는 동안 목표를 다시 골랐다. 지난 계획의 문제는 승인하지 않는다.
                candidate.reject(SUPERSEDED);
            } else if (outcome.approved()) {
                candidate.approve(outcome.note());
            } else {
                candidate.reject(outcome.note());
                log.info("문제 {} 품질 검사 탈락 ({}/{}): {}", candidateId, round, policy.maxGenerations(), outcome.note());
            }
            return candidate;
        });
    }

    /**
     * 새 계획으로 다시 만들기 전에, 지난 계획에서 승인된 첫 학습 문제를 쓰지 않게 한다. 남겨 두면 새 계획의 같은 자리와 섞이고,
     * 새 문제가 그 문제와 중복으로 떨어진다. 이미 승인된 문제는 아직 풀이에 나오지 않았다(목표는 문제 준비 전에만 고른다).
     */
    private void releasePreviousPlan(Long sessionId) {
        transaction.executeWithoutResult(tx -> questions.findBySessionIdOrderByIdAsc(sessionId).stream()
                .filter(q -> q.getStatus() == QuestionStatus.APPROVED && q.getPlanPosition() != null)
                .forEach(q -> q.reject(SUPERSEDED)));
    }

    private List<String> stemsOf(Long memoryItemId) {
        return questions.findByMemoryItemIdOrderByIdAsc(memoryItemId).stream()
                .filter(q -> q.getStatus() != QuestionStatus.CANDIDATE)
                .map(Question::getStem).toList();
    }

    private List<String> approvedStems(Long memoryItemId, Long exceptQuestionId) {
        return questions.findByMemoryItemIdAndStatusOrderByIdAsc(memoryItemId, QuestionStatus.APPROVED).stream()
                .filter(q -> !q.getId().equals(exceptQuestionId))
                .map(Question::getStem).toList();
    }

    private static Question.Content content(Question q) {
        return new Question.Content(q.getType(), q.getStem(), q.getChoices(), q.getCorrectChoice(), q.getAnswerCriteria(),
                q.getModelAnswer(), q.getHint(), q.getExplanation(), q.getEvidenceTurns());
    }

    private static QuestionQualityState state(Context context, Long memoryItemId, Question.Content content, List<String> existing) {
        Context.ItemContext item = context.item(memoryItemId);
        return new QuestionQualityState(item.item().content(), item.item().kind().name(), item.correction(),
                context.evidence(item.item().sourceTurns()), content.type().name(), content.stem(), content.choices(),
                content.correctChoice(), content.answerCriteria(), existing);
    }

    /** 세션에서 문제 요청에 필요한 복습 단위·기억 항목·근거 발화. */
    /** 비동기에서도 불리므로 요청 사용자가 아니라 세션 소유자 기준으로 만든다. */
    private Context context(Long sessionId) {
        return Context.of(sessions.detailForProcessing(sessionId), sessions.ownerOf(sessionId));
    }

    private record Context(Long sessionId, Long userId, @Nullable String topicHint, Map<Long, LearningSessionDetail.Unit> units,
            Map<Long, ItemContext> items, Map<Integer, LearningSessionDetail.Turn> turns) {

        record ItemContext(LearningSessionDetail.Item item, Long unitId, @Nullable String correction) {
        }

        static Context of(LearningSessionDetail detail, Long userId) {
            Map<Integer, LearningSessionDetail.Turn> turns = new HashMap<>();
            detail.turns().forEach(turn -> turns.put(turn.index(), turn));
            Map<Long, LearningSessionDetail.Unit> units = new LinkedHashMap<>();
            Map<Long, ItemContext> items = new HashMap<>();
            for (LearningSessionDetail.Unit unit : detail.units()) {
                units.put(unit.id(), unit);
                for (LearningSessionDetail.Item item : unit.items()) {
                    String correction = item.kind() == MemoryItemKind.CONFUSION
                            ? item.sourceTurns().stream().map(turns::get).filter(t -> t != null && t.correction() != null)
                                    .map(LearningSessionDetail.Turn::correction).findFirst().orElse(null)
                            : null;
                    items.put(item.id(), new ItemContext(item, unit.id(), correction));
                }
            }
            return new Context(detail.id(), userId, detail.topicHint(), units, items, turns);
        }

        ItemContext item(Long memoryItemId) {
            ItemContext item = items.get(memoryItemId);
            if (item == null) {
                throw new IllegalArgumentException("세션 %d에 기억 항목 %d 없음".formatted(sessionId, memoryItemId));
            }
            return item;
        }

        List<UnitQuestionRequest.EvidenceTurn> evidence(List<Integer> sourceTurns) {
            Set<Integer> indexes = new LinkedHashSet<>(sourceTurns);
            return indexes.stream().sorted().map(turns::get).filter(t -> t != null)
                    .map(t -> new UnitQuestionRequest.EvidenceTurn(t.index(), t.text(),
                            t.aiVerdict() == null ? null : t.aiVerdict().name(), t.correction()))
                    .toList();
        }

        UnitQuestionRequest.Target target(String targetId, QuestionSpec spec, List<String> avoid) {
            ItemContext target = item(spec.memoryItemId());
            String related = spec.relatedItemId() == null ? null : item(spec.relatedItemId()).item().content();
            return new UnitQuestionRequest.Target(targetId, spec.memoryItemId(), target.item().kind(), target.item().content(),
                    target.correction(), target.item().sourceTurns(), spec.type(), spec.learningGoal(), related, List.copyOf(avoid));
        }

        /** 단위의 제외되지 않은 기억 항목 전체와, 대상들의 출처 발화를 담은 요청. */
        UnitQuestionRequest request(Long unitId, List<UnitQuestionRequest.Target> targets) {
            LearningSessionDetail.Unit unit = units.get(unitId);
            List<UnitQuestionRequest.KeyPoint> keyPoints = unit.items().stream()
                    .filter(item -> item.status() != MemoryItemStatus.EXCLUDED)
                    .map(item -> new UnitQuestionRequest.KeyPoint(item.id(), item.kind(), item.content()))
                    .toList();
            List<Integer> sources = new ArrayList<>();
            targets.forEach(t -> sources.addAll(t.sourceTurns()));
            return new UnitQuestionRequest(unit.title(), topicHint, keyPoints, evidence(sources), targets);
        }
    }
}
