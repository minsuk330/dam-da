package com.khack.review.practice.application;

import com.khack.review.analysis.application.LearningSessionDetail;
import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.analysis.domain.MemoryItemKind;
import com.khack.review.analysis.domain.MemoryItemStatus;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.MemoryStrengthService;
import com.khack.review.memory.domain.MemoryStrength;
import com.khack.review.practice.domain.FirstStudyComposer;
import com.khack.review.practice.domain.FirstStudyPlan;
import com.khack.review.practice.domain.FirstStudyPlanRepository;
import com.khack.review.practice.domain.LearningGoal;
import com.khack.review.practice.domain.LearningGoalSet;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 목표 선택과 첫 학습 문제 계획 (스펙 §7 3·4단계, §7.8). 사용자가 확인을 마친 세션에서만 고를 수 있고,
 * 문제를 만들기 시작하면(세션이 확인 완료를 지나면) 바꿀 수 없다.
 */
@Service
public class LearningGoalService {

    private final LearningSessionQueryService sessions;
    private final FirstStudyPlanRepository plans;
    private final MemoryStrengthService strengths;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final int maxQuestions;

    public LearningGoalService(LearningSessionQueryService sessions, FirstStudyPlanRepository plans,
            MemoryStrengthService strengths, CurrentUser currentUser, Clock clock, ApplicationEventPublisher events,
            @Value("${review.practice.first-study-max-questions:12}") int maxQuestions) {
        this.sessions = sessions;
        this.plans = plans;
        this.strengths = strengths;
        this.currentUser = currentUser;
        this.clock = clock;
        this.events = events;
        this.maxQuestions = maxQuestions;
    }

    /**
     * 선택 화면. {@code selected}와 {@code composition}은 이미 골랐으면 그 결과, 아니면 기본 선택(헷갈린 지점이 있으면 6번)과
     * 그 미리보기다.
     */
    public record Options(List<LearningGoal> available, List<LearningGoal> selected, boolean saved,
            FirstStudyComposer.Composition composition) {
    }

    @Transactional(readOnly = true)
    public Options options(Long sessionId) {
        LearningSessionDetail detail = sessions.detail(sessionId);
        List<FirstStudyComposer.Unit> units = units(detail);
        return plans.findBySessionId(sessionId)
                .map(plan -> new Options(List.of(LearningGoal.values()), plan.getGoals(), true,
                        FirstStudyComposer.compose(plan.getGoals(), units, maxQuestions)))
                .orElseGet(() -> {
                    List<LearningGoal> defaults = units.stream().flatMap(u -> u.items().stream())
                            .anyMatch(i -> i.kind() == MemoryItemKind.CONFUSION)
                            ? List.of(LearningGoal.CORRECT_MISCONCEPTION) : List.of();
                    return new Options(List.of(LearningGoal.values()), defaults, false,
                            FirstStudyComposer.compose(defaults, units, maxQuestions));
                });
    }

    /** 목표 1~3개(중복 없이)를 고르고, 기억 강도를 함께 주면 그것도 정한다. */
    @Transactional
    public FirstStudyComposer.Composition choose(Long sessionId, List<LearningGoal> goals, @Nullable MemoryStrength strength) {
        if (goals == null || goals.isEmpty() || goals.size() > LearningGoal.MAX_SELECTED || new HashSet<>(goals).size() != goals.size()) {
            throw new IllegalArgumentException("학습 목표는 서로 다른 1~%d개를 고르세요.".formatted(LearningGoal.MAX_SELECTED));
        }
        LearningSessionDetail detail = sessions.detail(sessionId);
        if (detail.status() != LearningSessionStatus.CONFIRMED) {
            throw new IllegalStateException("학습 세션 %d은(는) %s 상태라 학습 목표를 고를 수 없습니다. 확인을 마친 뒤, 문제를 만들기 전에 고릅니다."
                    .formatted(sessionId, detail.status()));
        }
        Long userId = currentUser.id();
        FirstStudyComposer.Composition composition = FirstStudyComposer.compose(goals, units(detail), maxQuestions);
        FirstStudyPlan plan = plans.findBySessionId(sessionId).orElseGet(() -> new FirstStudyPlan(userId, sessionId));
        plan.replace(goals, composition.questions(), clock.instant());
        FirstStudyPlan saved = plans.save(plan);
        if (strength != null) {
            strengths.choose(userId, sessionId, strength);
        }
        events.publishEvent(new LearningGoalSet(sessionId, userId, saved.getId(), List.copyOf(goals), composition.questions().size()));
        return composition;
    }

    private static List<FirstStudyComposer.Unit> units(LearningSessionDetail detail) {
        return detail.units().stream()
                .filter(unit -> !unit.excluded())
                .map(unit -> new FirstStudyComposer.Unit(unit.id(), unit.items().stream()
                        .filter(item -> item.status() != MemoryItemStatus.EXCLUDED)
                        .map(item -> new FirstStudyComposer.Item(item.id(), item.kind()))
                        .toList()))
                .filter(unit -> !unit.items().isEmpty())
                .toList();
    }
}
