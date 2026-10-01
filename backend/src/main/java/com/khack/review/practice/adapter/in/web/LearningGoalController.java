package com.khack.review.practice.adapter.in.web;

import com.khack.review.analysis.application.LearningSessionNotFoundException;
import com.khack.review.memory.application.SessionNotFoundException;
import com.khack.review.memory.domain.MemoryStrength;
import com.khack.review.practice.application.LearningGoalService;
import com.khack.review.practice.domain.FirstStudyComposer;
import com.khack.review.practice.domain.LearningGoal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 학습 목표 선택과 첫 학습 문제 계획 (스펙 §7.8). */
@RestController
class LearningGoalController {

    private final LearningGoalService goals;

    LearningGoalController(LearningGoalService goals) {
        this.goals = goals;
    }

    record GoalView(LearningGoal goal, int number, String label, String description) {

        static GoalView of(LearningGoal goal) {
            return new GoalView(goal, goal.number(), goal.label(), goal.description());
        }
    }

    record OptionsView(List<GoalView> available, List<LearningGoal> selected, boolean saved, int maxSelected,
            FirstStudyComposer.Composition plan) {
    }

    record ChooseRequest(List<LearningGoal> goals, MemoryStrength strength) {
    }

    record ErrorResponse(String code, String message) {
    }

    @GetMapping("/api/sessions/{sessionId}/learning-goals")
    OptionsView options(@PathVariable Long sessionId) {
        LearningGoalService.Options options = goals.options(sessionId);
        return new OptionsView(options.available().stream().map(GoalView::of).toList(), options.selected(), options.saved(),
                LearningGoal.MAX_SELECTED, options.composition());
    }

    @PutMapping("/api/sessions/{sessionId}/learning-goals")
    FirstStudyComposer.Composition choose(@PathVariable Long sessionId, @RequestBody ChooseRequest request) {
        return goals.choose(sessionId, request.goals(), request.strength());
    }

    @ExceptionHandler({LearningSessionNotFoundException.class, SessionNotFoundException.class})
    ResponseEntity<ErrorResponse> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("not_found", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("invalid_state", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_goals", e.getMessage()));
    }
}
