package com.khack.review.engagement.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRecordRepository extends JpaRepository<DailyRecord, Long> {

    Optional<DailyRecord> findByUserIdAndDate(Long userId, LocalDate date);

    List<DailyRecord> findByUserIdOrderByDateAsc(Long userId);

    void deleteByUserId(Long userId);
}
