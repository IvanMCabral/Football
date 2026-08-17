package com.footballmanager.application.service.security;

/** Deliberately opaque denial for a missing or foreign private career. */
public final class CareerOwnershipDeniedException extends RuntimeException {
    public CareerOwnershipDeniedException() {
        super("private career is not available");
    }

    public CareerOwnershipDeniedException(Throwable cause) {
        super("private career is not available", cause);
    }
}
