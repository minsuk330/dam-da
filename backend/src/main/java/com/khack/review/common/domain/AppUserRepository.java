package com.khack.review.common.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByName(String name);

    Optional<AppUser> findByProviderAndProviderUserId(String provider, String providerUserId);
}
