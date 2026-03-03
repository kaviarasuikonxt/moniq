package com.moniq.api.auth.api;

import com.moniq.api.auth.refresh.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(RefreshTokenService.UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleRefreshUnauthorized(RefreshTokenService.UnauthorizedException ex) {
        return ResponseEntity.status(401).body(Map.of(
                "error", "unauthorized",
                "message", ex.getMessage()
        ));
    }
}