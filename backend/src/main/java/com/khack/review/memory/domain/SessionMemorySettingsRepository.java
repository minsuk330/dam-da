package com.khack.review.memory.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionMemorySettingsRepository extends JpaRepository<SessionMemorySettings, Long> {

    List<SessionMemorySettings> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
