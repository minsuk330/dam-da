package com.khack.review.memory.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.memory.application.KnowledgeGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기억 탭의 지식 그래프 (스펙 §7.10): 분야 → 소분류 → 세션 → 복습 단위와 노드별 기억 게이지 값. */
@RestController
class KnowledgeGraphController {

    private final KnowledgeGraphService graph;
    private final CurrentUser currentUser;

    KnowledgeGraphController(KnowledgeGraphService graph, CurrentUser currentUser) {
        this.graph = graph;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/knowledge-graph")
    KnowledgeGraphService.KnowledgeGraph graph() {
        return graph.graph(currentUser.id());
    }
}
