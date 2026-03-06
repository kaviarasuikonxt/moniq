// src/main/java/com/moniq/api/security/CurrentUserIdResolver.java
package com.moniq.api.security;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class CurrentUserIdResolver {

    private final NamedParameterJdbcTemplate jdbc;

    public CurrentUserIdResolver(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Resolves current userId in a way that works with Day 6/7 custom JJWT auth:
     * - if Authentication.getName() is UUID => use it
     * - else assume it's email/username => query users table for id
     */
    public Optional<UUID> resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        // 1) If name is UUID
        String name = auth.getName();
        UUID byName = tryParseUuid(name);
        if (byName != null) {
            return Optional.of(byName);
        }

        // 2) If principal is UUID string
        Object principal = auth.getPrincipal();
        if (principal != null) {
            UUID byPrincipal = tryParseUuid(String.valueOf(principal));
            if (byPrincipal != null) {
                return Optional.of(byPrincipal);
            }
        }

        // 3) Fallback: treat auth.getName() as email and resolve via DB
        if (name != null && !name.isBlank()) {
            return findUserIdByEmail(name);
        }

        return Optional.empty();
    }

    private Optional<UUID> findUserIdByEmail(String email) {
        // IMPORTANT:
        // This assumes your user table is named "users" with columns: id (UUID) and email (text).
        // If your table/column names differ, change SQL accordingly.
        String sql = "select id from users where lower(email) = lower(:email) limit 1";

        try {
            UUID id = jdbc.query(sql, Map.of("email", email), rs -> {
                if (rs.next()) {
                    return (UUID) rs.getObject("id");
                }
                return null;
            });
            return Optional.ofNullable(id);
        } catch (Exception ignored) {
            // If table name differs or query fails, return empty rather than crash
            return Optional.empty();
        }
    }

    private static UUID tryParseUuid(String v) {
        if (v == null) return null;
        try {
            return UUID.fromString(v.trim());
        } catch (Exception e) {
            return null;
        }
    }
}