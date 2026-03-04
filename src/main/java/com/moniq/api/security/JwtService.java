// src/main/java/com/moniq/api/security/JwtService.java
package com.moniq.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.moniq.api.auth.UserEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JwtService {

  private final String issuer;
  private final long expMinutes;
  private final byte[] secret;

  private final SecretKey key;
  private final long accessTtlSeconds;

  public JwtService(
      @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-ttl-seconds:#{null}}") Long accessTtlSeconds,
      @Value("${app.jwt.exp-minutes:#{null}}") Long expMinutes
  ) {
    this.issuer = issuer;
    this.expMinutes = expMinutes;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtlSeconds = accessTtlSeconds;
  }

  public long getAccessTtlSeconds() {
    return accessTtlSeconds;
  }

  /** Day 5: used by login/v2 and refresh */
  public String generateAccessToken(UserEntity user) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(accessTtlSeconds);

    Set<String> roles = user.getRoles() == null ? Set.of() : user.getRoles();

    return Jwts.builder()
        .issuer(issuer) // ✅ keep consistent with legacy
        .subject(user.getId().toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .claim("email", user.getEmail())
        .claim("provider", user.getProvider().name())
        .claim("roles", roles) // ✅ REQUIRED for JwtAuthFilter + /api/me
        .signWith(key) // algorithm inferred; same secret => OK
        .compact();
  }

  /** Day 4 legacy token (keep as-is so you don’t break Day 4) */
  public String createToken(UUID userId, String email, Set<String> roles) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(expMinutes * 60L);

    return Jwts.builder()
        .issuer(issuer)
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .claim("email", email)
        .claim("roles", roles)
        .signWith(Keys.hmacShaKeyFor(secret), Jwts.SIG.HS256)
        .compact();
  }

  public JwtClaims parseAndValidate(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret))
        .build()
        .parseSignedClaims(token)
        .getPayload();

    String subject = claims.getSubject();
    String email = claims.get("email", String.class);

    // ✅ SAFE roles parsing (works if it's Set, List, or null)
    Set<String> roles = extractRoles(claims);

    return new JwtClaims(
        UUID.fromString(subject),
        email,
        roles
    );
  }

  private Set<String> extractRoles(Claims claims) {
    Object raw = claims.get("roles");
    if (raw == null) return Set.of();

    if (raw instanceof Collection<?> col) {
      return col.stream()
          .filter(Objects::nonNull)
          .map(Object::toString)
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .collect(Collectors.toSet());
    }

    // If somehow stored as a single string
    String asString = raw.toString().trim();
    if (asString.isBlank()) return Set.of();
    return Set.of(asString);
  }

  public record JwtClaims(UUID userId, String email, Set<String> roles) {
  }
}