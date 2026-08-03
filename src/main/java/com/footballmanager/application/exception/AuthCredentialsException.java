package com.footballmanager.application.exception;

/** Safe authentication failure for unknown users or invalid passwords. */
public class AuthCredentialsException extends IllegalArgumentException {
    public AuthCredentialsException(String message) {
        super(message);
    }
}
