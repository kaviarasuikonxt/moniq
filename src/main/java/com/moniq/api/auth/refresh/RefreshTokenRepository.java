package com.moniq.api.auth.refresh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt = ?2 where t.id = ?1 and t.revokedAt is null")
    int revokeById(UUID tokenId, Instant revokedAt);

    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt = ?2 where t.familyId = ?1 and t.revokedAt is null")
    int revokeAllByFamilyId(UUID familyId, Instant revokedAt);

    @Modifying
    @Query("delete from RefreshTokenEntity t where t.expiresAt < ?1")
    int deleteExpiredBefore(Instant cutoff);
}