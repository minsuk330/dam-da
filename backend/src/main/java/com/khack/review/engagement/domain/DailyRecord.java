package com.khack.review.engagement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/** 사용자의 하루 매일 학습 결과. 연속 일수는 이 기록으로 매번 계산한다({@link Streak}). */
@Entity
@Table(name = "daily_record", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "date"}))
public class DailyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DailyStatus status;

    protected DailyRecord() {
    }

    public DailyRecord(Long userId, LocalDate date, DailyStatus status) {
        this.userId = userId;
        this.date = date;
        this.status = status;
    }

    /** 빈 날로 기록했다가 나중에 큐를 끝냈으면 완료로 바꾼다. 완료는 되돌리지 않는다. */
    public void complete() {
        status = DailyStatus.COMPLETED;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getDate() {
        return date;
    }

    public DailyStatus getStatus() {
        return status;
    }
}
