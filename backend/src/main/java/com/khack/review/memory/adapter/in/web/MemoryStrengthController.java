package com.khack.review.memory.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.MemoryStrengthService;
import com.khack.review.memory.application.SessionNotFoundException;
import com.khack.review.memory.domain.MemoryStrength;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 학습 세션의 기억 강도 선택지(예상 하루 부담 포함)와 선택. */
@RestController
class MemoryStrengthController {

    private final MemoryStrengthService strengths;
    private final CurrentUser currentUser;

    MemoryStrengthController(MemoryStrengthService strengths, CurrentUser currentUser) {
        this.strengths = strengths;
        this.currentUser = currentUser;
    }

    record StrengthRequest(MemoryStrength strength) {
    }

    @GetMapping("/api/sessions/{sessionId}/memory-strength")
    MemoryStrengthService.Options options(@PathVariable Long sessionId) {
        return strengths.options(currentUser.id(), sessionId);
    }

    @PutMapping("/api/sessions/{sessionId}/memory-strength")
    ResponseEntity<Void> choose(@PathVariable Long sessionId, @RequestBody StrengthRequest request) {
        if (request.strength() == null) {
            return ResponseEntity.badRequest().build();
        }
        strengths.choose(currentUser.id(), sessionId, request.strength());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
