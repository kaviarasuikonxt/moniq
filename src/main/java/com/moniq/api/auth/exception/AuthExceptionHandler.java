// src/main/java/com/moniq/api/auth/exception/AuthExceptionHandler.java
package com.moniq.api.auth.exception;

import com.moniq.api.auth.refresh.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiErrorResponse.FieldErrorItem> fields = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldItem)
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleBadJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request", req, null);
    }

    /**
     * Your code currently throws IllegalArgumentException for business errors.
     * We map known values to correct status codes.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        String msg = safeMsg(ex);

        // 409
        if ("EMAIL_ALREADY_EXISTS".equalsIgnoreCase(msg)) {
            return build(HttpStatus.CONFLICT, "Email already exists", req, null);
        }

        // 401
        if ("INVALID_CREDENTIALS".equalsIgnoreCase(msg)) {
            return build(HttpStatus.UNAUTHORIZED, "Invalid credentials", req, null);
        }
        if ("USE_SOCIAL_LOGIN".equalsIgnoreCase(msg)) {
            return build(HttpStatus.UNAUTHORIZED, "Use social login for this account", req, null);
        }

        // default 400
        return build(HttpStatus.BAD_REQUEST, msg.isBlank() ? "Bad request" : msg, req, null);
    }

    /**
     * Your refresh token service uses a nested UnauthorizedException.
     * Map it as 401.
     */
    @ExceptionHandler(RefreshTokenService.UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshUnauthorized(RefreshTokenService.UnauthorizedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, safeMsg(ex), req, null);
    }

    /**
     * Your loginV2 currently throws RuntimeException with text messages.
     * Treat these as 401 (auth failures) for a clean API.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(RuntimeException ex, HttpServletRequest req) {
        String msg = safeMsg(ex);

        // Treat common auth failures as 401
        if (msg.toLowerCase().contains("invalid credentials")
                || msg.toLowerCase().contains("use google login")
                || msg.toLowerCase().contains("unauthorized")
                || msg.toLowerCase().contains("token")) {
            return build(HttpStatus.UNAUTHORIZED, msg, req, null);
        }

        // Else: don’t leak internal errors
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Forbidden", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req, null);
    }

    private ApiErrorResponse.FieldErrorItem toFieldItem(FieldError fe) {
        String msg = fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value";
        return new ApiErrorResponse.FieldErrorItem(fe.getField(), msg);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest req,
                                                  List<ApiErrorResponse.FieldErrorItem> fieldErrors) {
        String requestId = MDC.get("requestId");
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                requestId,
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }

    private String safeMsg(Throwable ex) {
        return (ex.getMessage() == null) ? "" : ex.getMessage().trim();
    }
}