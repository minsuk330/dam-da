package com.khack.review.practice.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    Optional<PracticeSession> findFirstByLearningSessionIdAndKindOrderByIdAsc(Long learningSessionId, PracticeKind kind);

    /** 사용자의 해당 시각 이후 시작한 첫 풀이. 하루 한 번 여는 매일 학습을 찾는 데 쓴다. */
    Optional<PracticeSession> findFirstByUserIdAndKindAndStartedAtGreaterThanEqualOrderByIdAsc(Long userId, PracticeKind kind,
            Instant startedAt);

    /** 풀이 세션 행을 잠그고 읽는다. 같은 세션의 피드백 저장(도움 노출·재확인 편성·피드백 기록)을 한 번에 하나씩 처리한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PracticeSession> findForUpdateById(Long id);
}
