package com.khack.review.collection.adapter.in.web;

import com.khack.review.collection.application.ConversationQueryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 앱의 수신 대화 확인 화면이 읽는 API. 확인·수정은 분석 컨텍스트의 학습 세션이 생긴 뒤 붙인다. */
@RestController
@RequestMapping("/api/conversations")
class ConversationController {

    private final ConversationQueryService conversations;

    ConversationController(ConversationQueryService conversations) {
        this.conversations = conversations;
    }

    @GetMapping
    List<ConversationSummaryResponse> list() {
        return conversations.list().stream().map(ConversationSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    ConversationDetailResponse get(@PathVariable String id) {
        return conversations.findBySessionId(id)
                .map(ConversationDetailResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
