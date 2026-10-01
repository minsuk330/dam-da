package com.khack.review.memory.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryStateRepository extends JpaRepository<MemoryState, Long> {

    Optional<MemoryState> findByMemoryItemId(Long memoryItemId);

    List<MemoryState> findByUserId(Long userId);
}
