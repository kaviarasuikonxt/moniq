package com.moniq.api.auth.refresh;

import com.moniq.api.auth.UserEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "ix_refresh_tokens_user_id", columnList = "user_id"),
        @Index(name = "ix_refresh_tokens_family_id", columnList = "family_id"),
        @Index(name = "ix_refresh_tokens_session_id", columnList = "session_id"),
        @Index(name = "ux_refresh_tokens_token_hash", columnList = "token_hash", unique = true)
})
public class RefreshTokenEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_token_id")
    private RefreshTokenEntity replacedByToken;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now) || expiresAt.equals(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    // Getters/Setters

    public UUID getId() { return id; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public RefreshTokenEntity getReplacedByToken() { return replacedByToken; }
    public void setReplacedByToken(RefreshTokenEntity replacedByToken) { this.replacedByToken = replacedByToken; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}