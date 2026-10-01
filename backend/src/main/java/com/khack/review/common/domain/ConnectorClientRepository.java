package com.khack.review.common.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectorClientRepository extends JpaRepository<ConnectorClient, String> {

    Optional<ConnectorClient> findByClientId(String clientId);
}
