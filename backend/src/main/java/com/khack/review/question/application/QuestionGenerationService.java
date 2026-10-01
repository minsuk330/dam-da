package com.khack.review.question.application;

import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.application.SessionContent;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConversationQueryService;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionPlan;
import com.khack.review.question.domain.QuestionPlanner;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionSource;
import com.khack.review.question.domain.QuestionTarget;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 확인된 학습 세션에서 첫 학습의 문제 후보를 만든다(스펙 §7.8). 무엇을 낼지는 코드가, 내용은 LLM이 정한다.
 * 저장한 후보는 품질 검사(Jev)를 통과해야 출제할 수 있으므로, 여기서는 세션 상태를 바꾸지 않는다.
 */
@Service
public class QuestionGenerationService {

    /** 문제를 만들 수 있는 세션 상태. 사용자가 복습 단위를 확인한 뒤다(스펙 규칙 12). */
    static final LearningSessionStatus REQUIRED_STATUS = LearningSessionStatus.CONFIRMED;

    private final LearningSessionQueryService sessions;
    private final ConversationQueryService conversations;
    private final QuestionDrafter drafter;
    private final QuestionRepository questions;
    private final Clock clock;

    public QuestionGenerationService(LearningSessionQueryService sessions, ConversationQueryService conversations,
            QuestionDrafter drafter, QuestionRepository questions, Clock clock) {
        this.sessions = sessions;
        this.conversations = conversations;
        this.drafter = drafter;
        this.questions = questions;
        this.clock = clock;
    }

    /**
     * 이미 문제가 있는 (기억 항목, 목표)는 다시 만들지 않는다. 일부가 실패했을 때 다시 호출하면 빠진 것만 만든다.
     *
     * @param goals 세션에 고른 학습 목표 1~3개
     * @throws IllegalStateException 세션이 아직 확인되지 않았을 때
     */
    public QuestionGenerationResult generate(Long sessionId, Collection<LearningGoal> goals) {
        SessionContent content = sessions.content(sessionId);
        if (content.status() != REQUIRED_STATUS) {
            throw new IllegalStateException("학습 세션 %d: %s 상태에서는 문제를 만들 수 없습니다. %s 뒤에 만듭니다."
                    .formatted(sessionId, content.status(), REQUIRED_STATUS));
        }
        QuestionSource source = QuestionSources.of(content, conversations.evidence(content.conversationId()).turns());
        QuestionPlan plan = QuestionPlanner.plan(source, goals);

        Set<Made> made = questions.findBySessionId(sessionId).stream()
                .map(q -> new Made(q.getMemoryItemId(), q.getGoal()))
                .collect(Collectors.toSet());
        List<QuestionTarget> targets = plan.targets().stream()
                .filter(t -> !made.contains(new Made(source.item(t).memoryItemId(), t.goal())))
                .toList();

        // LLM 호출이 길어 트랜잭션 밖에서 하고, 저장만 한 번에 한다.
        QuestionDrafter.Result drafted = drafter.draft(source, targets);
        Instant now = clock.instant();
        List<Question> saved = questions.saveAll(drafted.drafts().stream()
                .map(d -> Question.candidate(content.userId(), sessionId, source.item(d.target()).memoryItemId(),
                        d.target(), d.content(), QuestionPrompt.VERSION, now))
                .toList());
        return new QuestionGenerationResult(
                saved.stream().map(Question::getId).toList(),
                plan.deferred(),
                plan.emptyGoals(),
                plan.skipped(),
                drafted.failures());
    }

    private record Made(Long memoryItemId, LearningGoal goal) {
    }
}
