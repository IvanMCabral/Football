package com.footballmanager.application.exception;

/** Validation failure for an authentication request. */
public class AuthValidationException extends RuntimeException {
    public AuthValidationException(String message) {
        super(message);
    }
}
