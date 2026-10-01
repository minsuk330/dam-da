package com.khack.review.memory.application;

import com.khack.review.analysis.application.SessionItemsQuery;
import com.khack.review.analysis.application.SessionItemsQuery.ConfirmedItem;
import com.khack.review.analysis.application.SessionItemsQuery.ConfirmedItems;
import com.khack.review.analysis.domain.ReviewUnitsConfirmedByUser;
import com.khack.review.memory.domain.InitialMemoryStateSeeded;
import com.khack.review.memory.domain.InitialRating;
import io.github.openspacedrepetition.Rating;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 확인을 마치면 대화 신호로 기억 항목의 초기 평가를 한 번 정하고 FSRS 초기 상태를 계산한다
 * (스펙 §6.4.2, 규칙 18, 도메인 스토리 S1-9). 확인 완료와 같은 트랜잭션에서 실행된다. 평가 시각은 대화 시각이다.
 * 이미 등급을 받은 항목은 건드리지 않아, 다시 실행해도 재계산하지 않는다.
 */
@Service
public class InitialMemorySeeder {

    private static final Logger log = LoggerFactory.getLogger(InitialMemorySeeder.class);

    private final SessionItemsQuery sessionItems;
    private final MemoryStateService memory;
    private final ApplicationEventPublisher events;

    public InitialMemorySeeder(SessionItemsQuery sessionItems, MemoryStateService memory, ApplicationEventPublisher events) {
        this.sessionItems = sessionItems;
        this.memory = memory;
        this.events = events;
    }

    @EventListener
    @Transactional
    public void on(ReviewUnitsConfirmedByUser event) {
        seed(event.sessionId());
    }

    @Transactional
    public InitialMemoryStateSeeded seed(Long sessionId) {
        ConfirmedItems confirmed = sessionItems.confirmedItems(sessionId);
        List<Long> again = new ArrayList<>();
        List<Long> good = new ArrayList<>();
        List<Long> unrated = new ArrayList<>();
        for (ConfirmedItem item : confirmed.items()) {
            Optional<Rating> rating = InitialRating.of(item.sourceTurns());
            if (rating.isEmpty()) {
                unrated.add(item.memoryItemId());
                continue;
            }
            if (memory.isReviewed(item.memoryItemId())) {
                log.info("기억 항목 {}: 이미 등급이 있어 초기 평가를 건너뜀", item.memoryItemId());
                continue;
            }
            memory.seed(confirmed.userId(), item.memoryItemId(), rating.get(), confirmed.conversationAt());
            (rating.get() == Rating.AGAIN ? again : good).add(item.memoryItemId());
        }
        InitialMemoryStateSeeded seeded = new InitialMemoryStateSeeded(sessionId, List.copyOf(again), List.copyOf(good),
                List.copyOf(unrated));
        events.publishEvent(seeded);
        log.info("학습 세션 {} 초기 평가: Again {}, Good {}, 평가 없음 {}", sessionId, again.size(), good.size(), unrated.size());
        return seeded;
    }
}
