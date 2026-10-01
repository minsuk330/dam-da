package com.khack.review.engagement.adapter.in.web;

import com.khack.review.common.application.CurrentUser;
import com.khack.review.engagement.application.NotificationService;
import com.khack.review.engagement.domain.Notification;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱 안 알림 목록과 읽음 처리. */
@RestController
class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser currentUser;

    NotificationController(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    record NotificationView(Long id, String type, String title, String body, Long targetId, Instant createdAt, boolean read) {

        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType().name(), n.getTitle(), n.getBody(), n.getTargetId(),
                    n.getCreatedAt(), n.isRead());
        }
    }

    record NotificationsView(long unreadCount, List<NotificationView> items) {
    }

    @GetMapping("/api/notifications")
    NotificationsView list() {
        Long userId = currentUser.id();
        return new NotificationsView(notifications.unreadCount(userId),
                notifications.list(userId).stream().map(NotificationView::of).toList());
    }

    @PostMapping("/api/notifications/{id}/read")
    ResponseEntity<Void> read(@PathVariable Long id) {
        return notifications.markRead(currentUser.id(), id).isPresent()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/notifications/read-all")
    ResponseEntity<Void> readAll() {
        notifications.markAllRead(currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
