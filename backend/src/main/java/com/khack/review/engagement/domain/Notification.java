package com.khack.review.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 앱 안 알림 1건. 해커톤은 푸시 대신 앱 안 알림 목록으로 보여준다(스펙 §7.7).
 * 알림을 누르면 이동할 대상은 종류와 ID로 가리킨다(예: 학습 세션 12).
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1_000)
    private String body;

    /** 이동 대상 ID. {@link NotificationType#SESSION_READY}면 학습 세션 ID, {@link NotificationType#DAILY_LEARNING}이면 시작한 오늘의 풀이 ID. */
    private Long targetId;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    protected Notification() {
    }

    public Notification(Long userId, NotificationType type, String title, String body, Long targetId, Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.targetId = targetId;
        this.createdAt = createdAt;
    }

    /** 학습 내용 도착 알림 (스토리 S1-7). */
    public static Notification sessionReady(Long userId, Long sessionId, String topicHint, Instant createdAt) {
        String topic = topicHint == null || topicHint.isBlank() ? "새" : topicHint.strip();
        return new Notification(userId, NotificationType.SESSION_READY,
                "%s 학습 내용이 도착했어요".formatted(topic), "공부할 내용을 확인해 보세요.", sessionId, createdAt);
    }

    /** 매일 학습 알림 (스토리 S2-1). {@code practiceId}는 이미 시작한 오늘의 풀이가 있을 때만 있다. */
    public static Notification dailyLearning(Long userId, int minutes, int questions, boolean started, Long practiceId,
            Instant createdAt) {
        String body = started ? "시작한 오늘의 학습을 이어서 끝내 보세요."
                : "문제 %d개를 풀면 오늘 학습이 끝나요.".formatted(questions);
        return new Notification(userId, NotificationType.DAILY_LEARNING, "오늘의 학습 · 약 %d분".formatted(minutes), body,
                practiceId, createdAt);
    }

    /** 이미 읽었으면 처음 읽은 시각을 유지한다. */
    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
