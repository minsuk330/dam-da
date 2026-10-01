package com.khack.review.memory.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.MemoryModelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 기억 패턴: 개인화 진행도, 망각 곡선, 기억 유지 기간 (스펙 §6.4.9). */
@RestController
class MemoryModelController {

    private final MemoryModelService models;
    private final CurrentUser currentUser;

    MemoryModelController(MemoryModelService models, CurrentUser currentUser) {
        this.models = models;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/memory/model")
    MemoryModelService.MemoryModel model() {
        return models.model(currentUser.id());
    }
}
