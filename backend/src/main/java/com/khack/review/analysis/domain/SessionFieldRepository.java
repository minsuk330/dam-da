package com.khack.review.analysis.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionFieldRepository extends JpaRepository<SessionField, Long> {

    List<SessionField> findAllBySessionIdIn(Collection<Long> sessionIds);

    void deleteBySessionIdIn(Collection<Long> sessionIds);
}
