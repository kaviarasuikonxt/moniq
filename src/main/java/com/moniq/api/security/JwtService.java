package com.moniq.api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

  private final String issuer;
  private final int expMinutes;
  private final byte[] secret;

  public JwtService(
      @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.exp-minutes}") int expMinutes,
      @Value("${app.jwt.secret}") String secret
  ) {
    this.issuer = issuer;
    this.expMinutes = expMinutes;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
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

    return new JwtClaims(
        UUID.fromString(claims.getSubject()),
        claims.get("email", String.class),
        claims.get("roles", Set.class)
    );
  }

  public record JwtClaims(UUID userId, String email, Set<String> roles) {}
}