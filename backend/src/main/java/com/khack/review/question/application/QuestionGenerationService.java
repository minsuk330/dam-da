package com.khack.review.question.application;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.question.domain.LearningGoal;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionPlan;
import com.khack.review.question.domain.QuestionPlanner;
import com.khack.review.question.domain.QuestionRepository;
import com.khack.review.question.domain.QuestionSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 확인된 학습 세션에서 첫 학습의 문제 후보를 만든다(스펙 §7.8). 무엇을 낼지는 코드가, 내용은 LLM이 정한다.
 * 저장한 후보는 품질 검사(Jev)를 통과해야 출제할 수 있다.
 */
@Service
public class QuestionGenerationService {

    private final QuestionDrafter drafter;
    private final QuestionRepository questions;
    private final CurrentUser currentUser;
    private final Clock clock;

    public QuestionGenerationService(QuestionDrafter drafter, QuestionRepository questions, CurrentUser currentUser,
            Clock clock) {
        this.drafter = drafter;
        this.questions = questions;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * @param source 사용자가 앱에서 확인을 마친 발화와 복습 단위. 확인 전의 세션으로 호출하지 않는다
     * @param goals  세션에 고른 학습 목표 1~3개
     */
    public QuestionGenerationResult generate(QuestionSource source, Collection<LearningGoal> goals) {
        QuestionPlan plan = QuestionPlanner.plan(source, goals);
        // LLM 호출이 길어 트랜잭션 밖에서 하고, 저장만 한 번에 한다.
        QuestionDrafter.Result drafted = drafter.draft(source, plan.targets());
        Long userId = currentUser.id();
        Instant now = clock.instant();
        List<Question> saved = questions.saveAll(drafted.drafts().stream()
                .map(d -> Question.candidate(userId, source.sessionId(), d.target(), d.content(),
                        QuestionPrompt.VERSION, now))
                .toList());
        return new QuestionGenerationResult(
                saved.stream().map(Question::getId).toList(),
                plan.deferred(),
                plan.emptyGoals(),
                plan.skipped(),
                drafted.failures());
    }
}
