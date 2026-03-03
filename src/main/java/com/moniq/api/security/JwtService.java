package com.moniq.api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.moniq.api.auth.UserEntity;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

  private final String issuer;
  private final int expMinutes;
  private final byte[] secret;

  private final Key key;
  private final long accessTtlSeconds;

  public JwtService(
      @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.exp-minutes}") int expMinutes,
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-ttl-seconds}") long accessTtlSeconds) {
    this.issuer = issuer;
    this.expMinutes = expMinutes;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtlSeconds = accessTtlSeconds;
  }

  public long getAccessTtlSeconds() {
    return accessTtlSeconds;
  }

  public String generateAccessToken(UserEntity user) {
    Instant now = Instant.now();
    Instant exp = now.plusSeconds(accessTtlSeconds);

    return Jwts.builder()
        .setSubject(user.getId().toString())
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(exp))
        .addClaims(Map.of(
            "email", user.getEmail(),
            "provider", user.getProvider().name()))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

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
    var claims = Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret))
        .build()
        .parseSignedClaims(token)
        .getPayload();

    @SuppressWarnings("unchecked")
    Set<String> roles = (Set<String>) claims.get("roles", Set.class);

    return new JwtClaims(
        UUID.fromString(claims.getSubject()),
        claims.get("email", String.class),
        roles);
  }

  public record JwtClaims(UUID userId, String email, Set<String> roles) {
  }
}