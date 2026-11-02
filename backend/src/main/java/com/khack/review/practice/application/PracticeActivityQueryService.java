package com.khack.review.practice.application;

import com.khack.review.practice.domain.AttemptTime;
import com.khack.review.practice.domain.PracticeAttemptRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자별로 답을 낸 날(서버 시간대). 사용 지표(#182)가 다른 컨텍스트에서 읽는다. */
@Service
public class PracticeActivityQueryService {

    private final PracticeAttemptRepository attempts;
    private final Clock clock;

    public PracticeActivityQueryService(PracticeAttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<Long, SortedSet<LocalDate>> attemptDaysByUser() {
        Map<Long, SortedSet<LocalDate>> days = new HashMap<>();
        for (AttemptTime attempt : attempts.findAllAttemptTimes()) {
            days.computeIfAbsent(attempt.userId(), id -> new TreeSet<>())
                    .add(LocalDate.ofInstant(attempt.submittedAt(), clock.getZone()));
        }
        return days;
    }
}
