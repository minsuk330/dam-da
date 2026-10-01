package com.khack.review.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationTest {

    static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void sessionReadyNamesTheTopicAndPointsToTheSession() {
        Notification notification = Notification.sessionReady(1L, 12L, "인덱스와 잠금", NOW);

        assertThat(notification.getType()).isEqualTo(NotificationType.SESSION_READY);
        assertThat(notification.getTitle()).isEqualTo("인덱스와 잠금 학습 내용이 도착했어요");
        assertThat(notification.getBody()).isEqualTo("공부할 내용을 확인해 보세요.");
        assertThat(notification.getTargetId()).isEqualTo(12L);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void sessionReadyWithoutTopicStillReads() {
        assertThat(Notification.sessionReady(1L, 12L, " ", NOW).getTitle()).isEqualTo("새 학습 내용이 도착했어요");
    }

    @Test
    void markReadKeepsTheFirstReadTime() {
        Notification notification = Notification.sessionReady(1L, 12L, null, NOW);

        notification.markRead(NOW.plusSeconds(10));
        notification.markRead(NOW.plusSeconds(20));

        assertThat(notification.getReadAt()).isEqualTo(NOW.plusSeconds(10));
    }
}
