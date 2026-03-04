// src/main/java/com/moniq/api/auth/exception/InvalidTokenException.java
package com.moniq.api.auth.exception;

public class InvalidTokenException extends AuthException {
    public InvalidTokenException(String message) {
        super(message);
    }
}