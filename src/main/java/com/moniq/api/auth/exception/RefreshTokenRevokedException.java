// src/main/java/com/moniq/api/auth/exception/RefreshTokenRevokedException.java
package com.moniq.api.auth.exception;

public class RefreshTokenRevokedException extends AuthException {
    public RefreshTokenRevokedException(String message) {
        super(message);
    }
}