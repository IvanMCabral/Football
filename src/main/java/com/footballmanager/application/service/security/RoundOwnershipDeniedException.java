package com.footballmanager.application.service.security;

/** Deliberately opaque denial for a missing or foreign private round. */
public final class RoundOwnershipDeniedException extends RuntimeException {
    public RoundOwnershipDeniedException() {
        super("private round is not available");
    }
}
