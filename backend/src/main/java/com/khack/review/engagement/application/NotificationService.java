package com.khack.review.engagement.application;

import com.khack.review.analysis.domain.LearningSessionReadyForConfirmation;
import com.khack.review.engagement.application.port.out.PushNotifier;
import com.khack.review.engagement.domain.Notification;
import com.khack.review.engagement.domain.NotificationRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앱 안 알림을 만들고 조회·읽음 처리한다. 푸시는 붙일 자리만 있고, 실패해도 앱 안 알림은 남는다. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final PushNotifier push;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, PushNotifier push, Clock clock) {
        this.notifications = notifications;
        this.push = push;
        this.clock = clock;
    }

    /** 검수를 마친 세션의 "학습 내용 도착" 알림. 세션 상태 변경과 같은 트랜잭션에서 저장된다. */
    @EventListener
    @Transactional
    public void on(LearningSessionReadyForConfirmation event) {
        Notification saved = notifications.save(
                Notification.sessionReady(event.userId(), event.sessionId(), event.topicHint(), clock.instant()));
        try {
            push.push(saved.getUserId(), saved.getTitle(), saved.getBody());
        } catch (RuntimeException e) {
            log.warn("push failed for notification {}", saved.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> list(Long userId) {
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** 사용자의 알림이 아니면 비어 있다. */
    @Transactional
    public Optional<Notification> markRead(Long userId, Long notificationId) {
        return notifications.findByIdAndUserId(notificationId, userId).map(notification -> {
            notification.markRead(clock.instant());
            return notification;
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.findByUserIdAndReadAtIsNull(userId).forEach(notification -> notification.markRead(clock.instant()));
    }
}
