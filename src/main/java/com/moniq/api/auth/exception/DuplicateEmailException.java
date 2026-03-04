// src/main/java/com/moniq/api/auth/exception/DuplicateEmailException.java
package com.moniq.api.auth.exception;

public class DuplicateEmailException extends AuthException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}