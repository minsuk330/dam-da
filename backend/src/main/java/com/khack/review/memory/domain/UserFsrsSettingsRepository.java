package com.khack.review.memory.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFsrsSettingsRepository extends JpaRepository<UserFsrsSettings, Long> {

    void deleteByUserId(Long userId);
}
