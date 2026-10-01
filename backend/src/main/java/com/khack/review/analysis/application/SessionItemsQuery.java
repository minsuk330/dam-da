package com.khack.review.analysis.application;

import com.khack.review.analysis.domain.LearningSessionRepository;
import com.khack.review.analysis.domain.MemoryItem;
import com.khack.review.analysis.domain.MemoryItemStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 컨텍스트가 학습 세션의 기억 항목을 ID로 조회하는 곳. */
@Service
public class SessionItemsQuery {

    private final LearningSessionRepository sessions;

    public SessionItemsQuery(LearningSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** 사용자의 세션이면 제외되지 않은 기억 항목 ID, 아니면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<List<Long>> activeItemIds(Long userId, Long sessionId) {
        return sessions.findById(sessionId)
                .filter(session -> Objects.equals(session.getUserId(), userId))
                .map(session -> session.items().stream()
                        .filter(item -> item.getStatus() != MemoryItemStatus.EXCLUDED)
                        .map(MemoryItem::getId)
                        .toList());
    }
}
