package com.khack.review.collection.adapter.in.web;

import com.khack.review.analysis.application.LearningSessionQueryService;
import com.khack.review.collection.application.ConversationQueryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 앱의 수신 대화 확인 화면이 읽는 API. 대화로 만든 학습 세션 ID를 함께 준다. 이 웹 어댑터만 분석 컨텍스트의 조회 서비스를
 * 부른다(docs/architecture.md 컨텍스트 의존 방향의 예외).
 */
@RestController
@RequestMapping("/api/conversations")
class ConversationController {

    private final ConversationQueryService conversations;
    private final LearningSessionQueryService sessions;

    ConversationController(ConversationQueryService conversations, LearningSessionQueryService sessions) {
        this.conversations = conversations;
        this.sessions = sessions;
    }

    @GetMapping
    List<ConversationSummaryResponse> list() {
        return conversations.mine().stream()
                .map(c -> ConversationSummaryResponse.from(c, sessions.sessionIdOf(c.id()).orElse(null)))
                .toList();
    }

    @GetMapping("/{id}")
    ConversationDetailResponse get(@PathVariable String id) {
        return conversations.findMineBySessionId(id)
                .map(c -> ConversationDetailResponse.from(c, sessions.sessionIdOf(c.id()).orElse(null)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
