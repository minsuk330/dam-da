package com.khack.review.question.application;

import com.khack.review.analysis.application.LearningSessionDetail;
import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.application.SessionProgressService;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.question.application.port.out.GeneratedQuestion;
import com.khack.review.question.application.port.out.QuestionGenerator;
import com.khack.review.question.application.port.out.QuestionRequest;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionSpec;
import com.khack.review.question.domain.QuestionStatus;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 문제 생성·품질 검사·변형 (스펙 §6.1, §6.2, 규칙 3·7·12). 생성(LLM)과 검사(Jev)는 오래 걸리므로 트랜잭션 밖에서 하고,
 * 저장과 상태 변경만 짧은 트랜잭션으로 묶는다. 한 자리에 정해진 횟수까지 생성을 시도하고, 모두 떨어지면 그 항목 출제를 보류한다.
 */
@Service
public class QuestionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationService.class);

    private final ObjectProvider<QuestionGenerator> generator;
    private final QuestionQualityJudge judge;
    private final QuestionQualityPolicy policy;
    private final QuestionRepository questions;
    private final LearningSessionQueryService sessions;
    private final SessionProgressService progress;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public QuestionGenerationService(ObjectProvider<QuestionGenerator> generator, QuestionQualityJudge judge,
            QuestionQualityPolicy policy, QuestionRepository questions, LearningSessionQueryService sessions,
            SessionProgressService progress, CurrentUser currentUser, Clock clock, TransactionTemplate transaction) {
        this.generator = generator;
        this.judge = judge;
        this.policy = policy;
        this.questions = questions;
        this.sessions = sessions;
        this.progress = progress;
        this.currentUser = currentUser;
        this.clock = clock;
        this.transaction = transaction;
    }

    /**
     * 첫 학습 계획의 문제를 만든다. 확인 완료 상태의 세션만 하며(규칙 12), 끝나면 세션을 문제 준비로 넘긴다.
     * 승인되지 못한 자리는 출제를 보류한다(그 항목은 신규로 남는다).
     */
    public void generateFirstStudy(Long sessionId, List<QuestionSpec> specs) {
        LearningSessionDetail detail = sessions.detail(sessionId);
        if (detail.status() != LearningSessionStatus.CONFIRMED) {
            log.info("학습 세션 {}: {} 상태라 첫 학습 문제를 만들지 않음", sessionId, detail.status());
            return;
        }
        Context context = Context.of(detail, currentUser.id());
        int approved = 0;
        for (QuestionSpec spec : specs) {
            if (generateApproved(context, spec, null).isPresent()) {
                approved++;
            }
        }
        progress.markQuestionsReady(sessionId);
        log.info("학습 세션 {} 첫 학습 문제: 계획 {}, 승인 {}, 보류 {}", sessionId, specs.size(), approved, specs.size() - approved);
    }

    /** 같은 기억 항목·유형의 변형 문제(규칙 7). 기존 문제와 다른 표현을 요청하며, 기억 상태는 항목 단위라 그대로 공유된다. */
    public Optional<Question> requestVariant(Long questionId) {
        Question original = questions.findById(questionId).orElseThrow(() -> new IllegalArgumentException("문제 없음: " + questionId));
        Context context = Context.of(sessions.detail(original.getSessionId()), currentUser.id());
        QuestionSpec spec = new QuestionSpec(-1, original.getMemoryItemId(), null, original.getType(), null);
        return generateApproved(context, spec, original.getId());
    }

    /**
     * 풀이에서 모호하다고 보류된 승인 문제를 다시 검사한다(스펙 §6.4.5 보류 처리). 통과하면 그대로 쓰고,
     * 떨어지면 폐기하고 변형 문제를 만든다. 쓸 수 있는 문제를 돌려준다.
     */
    public Optional<Question> recheck(Long questionId) {
        Question question = questions.findById(questionId).orElseThrow(() -> new IllegalArgumentException("문제 없음: " + questionId));
        Context context = Context.of(sessions.detail(question.getSessionId()), currentUser.id());
        QualityOutcome outcome = judge.judge(state(context, question.getMemoryItemId(), question.getType().name(), content(question),
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

    private Optional<Question> generateApproved(Context context, QuestionSpec spec, @Nullable Long variantOf) {
        QuestionGenerator available = generator.getIfAvailable();
        List<String> tried = new ArrayList<>(stemsOf(spec.memoryItemId()));
        for (int attempt = 1; attempt <= policy.maxGenerations(); attempt++) {
            if (available == null) {
                log.warn("문제 생성기가 구성되지 않아 항목 {} 출제를 보류", spec.memoryItemId());
                return Optional.empty();
            }
            GeneratedQuestion generated;
            try {
                generated = available.generate(context.request(spec, tried));
            } catch (RuntimeException e) {
                log.warn("항목 {} 문제 생성 실패 ({}/{}): {}", spec.memoryItemId(), attempt, policy.maxGenerations(), e.getMessage());
                continue;
            }
            Question.Content content = new Question.Content(spec.type(), generated.stem(), generated.choices(),
                    generated.correctChoice(), generated.answerCriteria(), generated.modelAnswer(), generated.evidenceTurns());
            int attemptNo = attempt;
            Long candidateId = transaction.execute(tx -> questions.save(Question.candidate(context.userId(), context.sessionId(),
                    spec.memoryItemId(), spec.position() < 0 ? null : spec.position(), spec.learningGoal(), variantOf, attemptNo,
                    content, clock.instant())).getId());
            QualityOutcome outcome = judge.judge(state(context, spec.memoryItemId(), spec.type().name(), content,
                    approvedStems(spec.memoryItemId(), candidateId)));
            Question decided = transaction.execute(tx -> {
                Question candidate = questions.findById(candidateId).orElseThrow();
                if (outcome.approved()) {
                    candidate.approve(outcome.note());
                } else {
                    candidate.reject(outcome.note());
                }
                return candidate;
            });
            if (decided.getStatus() == QuestionStatus.APPROVED) {
                return Optional.of(decided);
            }
            tried.add(generated.stem());
            log.info("항목 {} 문제 {} 품질 검사 탈락 ({}/{}): {}", spec.memoryItemId(), candidateId, attempt, policy.maxGenerations(), outcome.note());
        }
        return Optional.empty();
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
                q.getModelAnswer(), q.getEvidenceTurns());
    }

    private static QuestionQualityState state(Context context, Long memoryItemId, String type, Question.Content content,
            List<String> existing) {
        Context.ItemContext item = context.item(memoryItemId);
        return new QuestionQualityState(item.item().content(), item.item().kind().name(), item.correction(),
                context.evidence(item.item().sourceTurns()), type, content.stem(), content.choices(), content.correctChoice(),
                content.answerCriteria(), existing);
    }

    /** 세션에서 문제 요청에 필요한 기억 항목·근거 발화. */
    private record Context(Long sessionId, Long userId, @Nullable String topicHint, Map<Long, ItemContext> items,
            Map<Integer, LearningSessionDetail.Turn> turns) {

        record ItemContext(LearningSessionDetail.Item item, String unitTitle, @Nullable String correction) {
        }

        static Context of(LearningSessionDetail detail, Long userId) {
            Map<Integer, LearningSessionDetail.Turn> turns = new HashMap<>();
            detail.turns().forEach(turn -> turns.put(turn.index(), turn));
            Map<Long, ItemContext> items = new HashMap<>();
            for (LearningSessionDetail.Unit unit : detail.units()) {
                for (LearningSessionDetail.Item item : unit.items()) {
                    String correction = item.kind() == MemoryItemKind.CONFUSION
                            ? item.sourceTurns().stream().map(turns::get).filter(t -> t != null && t.correction() != null)
                                    .map(LearningSessionDetail.Turn::correction).findFirst().orElse(null)
                            : null;
                    items.put(item.id(), new ItemContext(item, unit.title(), correction));
                }
            }
            return new Context(detail.id(), userId, detail.topicHint(), items, turns);
        }

        ItemContext item(Long memoryItemId) {
            ItemContext item = items.get(memoryItemId);
            if (item == null) {
                throw new IllegalArgumentException("세션 %d에 기억 항목 %d 없음".formatted(sessionId, memoryItemId));
            }
            return item;
        }

        List<QuestionRequest.EvidenceTurn> evidence(List<Integer> sourceTurns) {
            Set<Integer> indexes = new LinkedHashSet<>(sourceTurns);
            return indexes.stream().sorted().map(turns::get).filter(t -> t != null)
                    .map(t -> new QuestionRequest.EvidenceTurn(t.index(), t.text(),
                            t.aiVerdict() == null ? null : t.aiVerdict().name(), t.correction()))
                    .toList();
        }

        QuestionRequest request(QuestionSpec spec, List<String> avoid) {
            ItemContext target = item(spec.memoryItemId());
            List<Integer> sources = new ArrayList<>(target.item().sourceTurns());
            String related = null;
            if (spec.relatedItemId() != null) {
                ItemContext other = item(spec.relatedItemId());
                related = other.item().content();
                sources.addAll(other.item().sourceTurns());
            }
            return new QuestionRequest(spec.memoryItemId(), target.item().kind(), target.item().content(), target.correction(),
                    target.unitTitle(), topicHint, evidence(sources), spec.learningGoal(), spec.type(), related, List.copyOf(avoid));
        }
    }
}
