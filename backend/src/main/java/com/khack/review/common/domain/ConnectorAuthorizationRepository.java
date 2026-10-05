package com.khack.review.common.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectorAuthorizationRepository extends JpaRepository<ConnectorAuthorization, String> {

    Optional<ConnectorAuthorization> findByStateHash(String hash);

    Optional<ConnectorAuthorization> findByCodeHash(String hash);

    Optional<ConnectorAuthorization> findByAccessTokenHash(String hash);

    Optional<ConnectorAuthorization> findByRefreshTokenHash(String hash);

    @Query("""
            select a from ConnectorAuthorization a
            where a.stateHash = :hash or a.codeHash = :hash or a.accessTokenHash = :hash or a.refreshTokenHash = :hash""")
    Optional<ConnectorAuthorization> findByAnyTokenHash(@Param("hash") String hash);

    void deleteByPrincipalName(String principalName);
}
