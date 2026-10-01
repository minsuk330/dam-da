package com.khack.review.practice.application;

import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.practice.domain.FirstStudyPlan;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.PlannedQuestionEntry;
import com.khack.review.question.application.QuestionGenerationService;
import com.khack.review.question.domain.Question;
import com.khack.review.question.domain.QuestionStatus;
import com.khack.review.question.domain.QuestionType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 학습 문제 준비 상황. 승인된 문제만 보여주고(규칙 3), 정답 기준·모범 답안은 내보내지 않는다.
 * 생성이 끝났는데 승인된 문제가 없는 자리는 출제 보류다.
 */
@Service
public class FirstStudyQueryService {

    private final LearningSessionQueryService sessions;
    private final FirstStudyPlanRepository plans;
    private final QuestionGenerationService questions;

    public FirstStudyQueryService(LearningSessionQueryService sessions, FirstStudyPlanRepository plans,
            QuestionGenerationService questions) {
        this.sessions = sessions;
        this.plans = plans;
        this.questions = questions;
    }

    public record QuestionView(Long questionId, int position, String learningGoal, QuestionType type, Long memoryItemId,
            String stem, List<String> choices) {
    }

    public record HeldSlot(int position, Long memoryItemId, QuestionType type) {
    }

    public record FirstStudy(LearningSessionStatus sessionStatus, boolean generating, int planned, List<QuestionView> questions,
            List<HeldSlot> held) {
    }

    @Transactional(readOnly = true)
    public FirstStudy firstStudy(Long sessionId) {
        LearningSessionStatus status = sessions.detail(sessionId).status();
        FirstStudyPlan plan = plans.findBySessionId(sessionId).orElse(null);
        if (plan == null) {
            return new FirstStudy(status, false, 0, List.of(), List.of());
        }
        Map<Integer, Question> approved = questions.questionsOf(sessionId).stream()
                .filter(q -> q.getStatus() == QuestionStatus.APPROVED && q.getPlanPosition() != null)
                .collect(Collectors.toMap(Question::getPlanPosition, Function.identity(), (first, later) -> later));
        boolean generating = status == LearningSessionStatus.CONFIRMED;
        List<QuestionView> views = plan.getQuestions().stream()
                .filter(entry -> approved.containsKey(entry.getPosition()))
                .map(entry -> view(entry, approved.get(entry.getPosition())))
                .toList();
        List<HeldSlot> held = generating ? List.of() : plan.getQuestions().stream()
                .filter(entry -> !approved.containsKey(entry.getPosition()))
                .map(entry -> new HeldSlot(entry.getPosition(), entry.getMemoryItemId(), entry.getQuestionType()))
                .toList();
        return new FirstStudy(status, generating, plan.getQuestions().size(), views, held);
    }

    private static QuestionView view(PlannedQuestionEntry entry, Question question) {
        return new QuestionView(question.getId(), entry.getPosition(), entry.getGoal().name(), question.getType(),
                question.getMemoryItemId(), question.getStem(), question.getChoices());
    }
}
