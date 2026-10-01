package com.khack.review.practice.adapter.in.web.dev;

import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.analysis.application.SessionConfirmationService;
import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.analysis.application.SessionProgressService;
import com.khack.review.analysis.domain.LearningSessionStatus;
import com.khack.review.collection.application.ConnectorIntakeService;
import com.khack.review.collection.domain.SavedSession;
import com.khack.review.collection.domain.SessionInput;
import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.domain.MemoryStrength;
import com.khack.review.practice.application.LearningGoalService;
import com.khack.review.practice.domain.LearningGoal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 계정에 시연용 학습 기록을 넣는 개발 도구. 앱과 같은 흐름(저장 → Jev 검수 → 확인 → 학습 목표 → 문제 생성·품질 검사)을
 * 그대로 타고, 대화 시각만 과거로 지정한다. 대상은 개발 도구 사용자({@code POST /dev/current-user})다.
 * 검수와 문제 생성은 비동기라 호출하는 쪽(optimizer의 시드 스크립트)이 상태를 보며 단계를 넘긴다. `/dev/**` 보호를 받는다.
 */
@RestController
class DevDemoSeedController {

    /** 목표를 주지 않았을 때 고를 순서. 고를 수 있는 것 중 앞에서 두 개를 쓴다. */
    private static final List<LearningGoal> DEFAULT_GOALS = List.of(
            LearningGoal.CORRECT_MISCONCEPTION, LearningGoal.PRINCIPLE, LearningGoal.KEY_RECALL, LearningGoal.DISTINGUISH);

    private final ConnectorIntakeService intake;
    private final LearningSessionQueryService sessions;
    private final SessionConfirmationService confirmation;
    private final LearningGoalService goals;
    private final SessionProgressService progress;
    private final SessionItemsQuery items;
    private final CurrentUser currentUser;

    DevDemoSeedController(ConnectorIntakeService intake, LearningSessionQueryService sessions, SessionConfirmationService confirmation,
            LearningGoalService goals, SessionProgressService progress, SessionItemsQuery items, CurrentUser currentUser) {
        this.intake = intake;
        this.sessions = sessions;
        this.confirmation = confirmation;
        this.goals = goals;
        this.progress = progress;
        this.items = items;
        this.currentUser = currentUser;
    }

    record SeedRequest(SessionInput session, Instant conversationAt) {
    }

    record SeededSession(Long sessionId, LearningSessionStatus status, Instant createdAt) {
    }

    record ConfirmRequest(@Nullable List<LearningGoal> goals, @Nullable MemoryStrength strength) {
    }

    record ConfirmedSession(Long sessionId, LearningSessionStatus status, List<LearningGoal> goals, int questions) {
    }

    record SeedItem(Long memoryItemId, Long sessionId, Instant conversationAt) {
    }

    record ErrorResponse(String code, String message) {
    }

    @PostMapping("/dev/demo-seed/sessions")
    SeededSession seed(@RequestBody SeedRequest request) {
        if (currentUser.isDemoDevUser()) {
            throw new IllegalStateException("기본 데모 사용자에게는 시연 기록을 넣지 않습니다. 먼저 POST /dev/current-user로 전환하세요.");
        }
        SavedSession saved = intake.intake(request.session(), request.conversationAt());
        Long sessionId = sessions.sessionIdOf(saved.id()).orElseThrow();
        return status(sessionId);
    }

    @GetMapping("/dev/demo-seed/sessions/{sessionId}")
    SeededSession status(@PathVariable Long sessionId) {
        var detail = sessions.detail(sessionId);
        return new SeededSession(sessionId, detail.status(), detail.createdAt());
    }

    /** 확인을 마치고(초기 평가) 학습 목표를 고른다(문제 생성 시작). 목표를 주지 않으면 고를 수 있는 것 중 기본 순서로 두 개. */
    @PostMapping("/dev/demo-seed/sessions/{sessionId}/confirm")
    ConfirmedSession confirm(@PathVariable Long sessionId, @RequestBody ConfirmRequest request) {
        confirmation.confirm(sessionId);
        List<LearningGoal> chosen = request.goals() == null || request.goals().isEmpty() ? defaultGoals(sessionId) : request.goals();
        int questions = goals.choose(sessionId, chosen, request.strength()).questions().size();
        return new ConfirmedSession(sessionId, sessions.detail(sessionId).status(), chosen, questions);
    }

    /** 첫 학습을 시작한 상태로 둔다. 문제 준비가 끝난 세션만 넘어간다. */
    @PostMapping("/dev/demo-seed/sessions/{sessionId}/start")
    SeededSession start(@PathVariable Long sessionId) {
        progress.markStudyStarted(sessionId);
        return status(sessionId);
    }

    /** 현재 사용자의 활성 기억 항목과 그 세션의 대화 시각. 합성 복습 기록을 붙일 대상이다. */
    @GetMapping("/dev/demo-seed/items")
    List<SeedItem> items() {
        Long userId = currentUser.id();
        Map<Long, Instant> conversationAt = new HashMap<>();
        List<SeedItem> result = new ArrayList<>();
        for (SessionItemsQuery.ActiveItem item : items.activeItemsOf(userId)) {
            Instant at = conversationAt.computeIfAbsent(item.sessionId(), id -> sessions.detail(id).createdAt());
            result.add(new SeedItem(item.memoryItemId(), item.sessionId(), at));
        }
        result.sort(Comparator.comparing(SeedItem::conversationAt).thenComparing(SeedItem::memoryItemId));
        return result;
    }

    private List<LearningGoal> defaultGoals(Long sessionId) {
        Set<LearningGoal> available = Set.copyOf(goals.options(sessionId).available());
        List<LearningGoal> chosen = DEFAULT_GOALS.stream().filter(available::contains).limit(2).toList();
        if (chosen.isEmpty()) {
            throw new IllegalStateException("학습 세션 %d에서 고를 수 있는 학습 목표가 없습니다.".formatted(sessionId));
        }
        return chosen;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ErrorResponse> invalid(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_input", e.getMessage()));
    }
}
