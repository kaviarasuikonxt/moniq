package com.moniq.api.auth.refresh;

import com.moniq.api.auth.UserEntity;
import com.moniq.api.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final JwtService jwtService;

    private final long refreshTtlSeconds;
    private final String refreshHashSecret;

    private final SecureRandom secureRandom = new SecureRandom();

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}

    public RefreshTokenService(
            RefreshTokenRepository repo,
            JwtService jwtService,
            @Value("${app.jwt.refresh-ttl-seconds}") long refreshTtlSeconds,
            @Value("${app.jwt.refresh-hash-secret:${app.jwt.secret}}") String refreshHashSecret
    ) {
        this.repo = repo;
        this.jwtService = jwtService;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.refreshHashSecret = refreshHashSecret;
    }

    /** Create access + refresh token (new session/device) */
    @Transactional
    public TokenPair issueNewSession(UserEntity user, HttpServletRequest request) {
        UUID familyId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        return issueTokens(user, familyId, sessionId, request);
    }

    /** Rotate refresh token (one-time-use). Returns new access + new refresh. */
    @Transactional
    public TokenPair refresh(String presentedRefreshToken, HttpServletRequest request) {
        String hash = hashToken(presentedRefreshToken);
        Instant now = Instant.now();

        RefreshTokenEntity existing = repo.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        // Expired?
        if (existing.isExpired(now)) {
            // Optionally revoke it as hygiene
            if (!existing.isRevoked()) {
                existing.setRevokedAt(now);
            }
            throw new UnauthorizedException("Refresh token expired");
        }

        // Reuse detection:
        // If it's already revoked AND it was replaced, this means a rotated token was reused -> revoke the whole family.
        if (existing.isRevoked()) {
            if (existing.getReplacedByToken() != null) {
                repo.revokeAllByFamilyId(existing.getFamilyId(), now);
                throw new UnauthorizedException("Refresh token reuse detected. Session revoked.");
            }
            throw new UnauthorizedException("Refresh token revoked");
        }

        // Rotate: revoke current and create next linked token
        UserEntity user = existing.getUser();
        UUID familyId = existing.getFamilyId();
        UUID sessionId = existing.getSessionId();

        RefreshTokenEntity next = createTokenRow(user, familyId, sessionId, request, now);

        existing.setRevokedAt(now);
        existing.setReplacedByToken(next);

        // New access token (stateless)
        String newAccess = jwtService.generateAccessToken(user);
        long expiresIn = jwtService.getAccessTtlSeconds();

        // Return the *plaintext* refresh token for the new row
        return new TokenPair(newAccess, nextPlainText(next), expiresIn);
    }

    /** Logout: revoke the presented token (and optionally the whole family if you prefer). */
    @Transactional
    public void logout(String presentedRefreshToken) {
        String hash = hashToken(presentedRefreshToken);
        Instant now = Instant.now();

        Optional<RefreshTokenEntity> opt = repo.findByTokenHash(hash);
        if (opt.isEmpty()) {
            // Idempotent logout
            return;
        }
        RefreshTokenEntity token = opt.get();
        if (!token.isRevoked()) {
            token.setRevokedAt(now);
        }
    }

    /** Internal: issue tokens for (familyId, sessionId) */
    private TokenPair issueTokens(UserEntity user, UUID familyId, UUID sessionId, HttpServletRequest request) {
        Instant now = Instant.now();
        RefreshTokenEntity rt = createTokenRow(user, familyId, sessionId, request, now);

        String access = jwtService.generateAccessToken(user);
        long expiresIn = jwtService.getAccessTtlSeconds();

        return new TokenPair(access, nextPlainText(rt), expiresIn);
    }

    /** Create a new DB row (hash stored) and temporarily keep plaintext in userAgent field (not persisted) */
    private RefreshTokenEntity createTokenRow(UserEntity user, UUID familyId, UUID sessionId, HttpServletRequest request, Instant now) {
        String refreshPlain = generateOpaqueToken();

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUser(user);
        entity.setFamilyId(familyId);
        entity.setSessionId(sessionId);

        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(refreshTtlSeconds, ChronoUnit.SECONDS));

        entity.setUserAgent(safeHeader(request, "User-Agent"));
        entity.setIpAddress(extractClientIp(request));

        entity.setTokenHash(hashToken(refreshPlain));

        // Save
        repo.save(entity);

        // Attach plaintext transiently (not mapped) via a trick:
        // we will store it in a thread-local-like way by returning it from nextPlainText(entity)
        // using an in-memory map is overkill; simplest is to return it directly from caller.
        // So: store plaintext into userAgent temporarily is NOT safe. We'll instead use a private static holder.
        PlainTextHolder.set(entity.getId(), refreshPlain);

        return entity;
    }

    /** Retrieve the plaintext token stored for this entity id during this transaction */
    private String nextPlainText(RefreshTokenEntity saved) {
        String plain = PlainTextHolder.get(saved.getId());
        if (plain == null) {
            throw new IllegalStateException("Refresh token plaintext not available");
        }
        PlainTextHolder.remove(saved.getId());
        return plain;
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[64]; // 512-bit
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Deterministic hash for DB lookup: HMAC-SHA256(secret, token) => hex */
    private String hashToken(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(refreshHashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] out = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return toHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String safeHeader(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String extractClientIp(HttpServletRequest request) {
        // Azure App Service / reverse proxy headers often use X-Forwarded-For
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    /** Minimal runtime exception for 401 mapping */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String msg) { super(msg); }
    }

    /**
     * Internal helper to safely pass plaintext token out of createTokenRow() without persisting it.
     * This is process-local and cleared immediately.
     */
    private static class PlainTextHolder {
        private static final java.util.concurrent.ConcurrentHashMap<UUID, String> MAP = new java.util.concurrent.ConcurrentHashMap<>();
        static void set(UUID id, String plain) { MAP.put(id, plain); }
        static String get(UUID id) { return MAP.get(id); }
        static void remove(UUID id) { MAP.remove(id); }
    }
}