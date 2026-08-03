package com.footballmanager.application.exception;

/** Conflict raised when registration would violate an authentication invariant. */
public class AuthConflictException extends IllegalArgumentException {
    public AuthConflictException(String message) {
        super(message);
    }
}
